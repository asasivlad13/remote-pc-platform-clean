import { useEffect, useRef, useState } from "react";
import type { RefObject } from "react";
import type { PcDetailsResponse } from "../pcs/pcTypes";

type VideoStatus =
    | "idle"
    | "connecting"
    | "signalling_connected"
    | "producer_search"
    | "session_starting"
    | "offer_received"
    | "track_received"
    | "playing"
    | "connected"
    | "reconnecting"
    | "error"
    | "closed";

type UseGStreamerWebRtcParams = {
    pc: PcDetailsResponse | null;
    pcId: number;
    pcName: string;
};

type UseGStreamerWebRtcResult = {
    videoRef: RefObject<HTMLVideoElement | null>;
    videoStatus: VideoStatus;
    signallingUrl: string | null;
    videoError: string;
    fps: number | null;
    latencyMs: number | null;
};

type ProducerInfo = {
    id?: string;
    peerId?: string;
    meta?: {
        name?: string;
        [key: string]: unknown;
    };
    [key: string]: unknown;
};

type SignalMessage = {
    type?: string;
    peerId?: string;
    producers?: ProducerInfo[];
    sessionId?: string;
    sdp?: {
        type: RTCSdpType;
        sdp: string;
    };
    ice?: {
        candidate?: string;
        sdpMLineIndex?: number;
    };
    message?: string;
    [key: string]: unknown;
};

export function useGStreamerWebRtc({
                                       pc,
                                       pcId,
                                       pcName,
                                   }: UseGStreamerWebRtcParams): UseGStreamerWebRtcResult {
    const videoRef = useRef<HTMLVideoElement | null>(null);

    const [videoStatus, setVideoStatus] = useState<VideoStatus>("idle");
    const [signallingUrl, setSignallingUrl] = useState<string | null>(null);
    const [videoError, setVideoError] = useState("");
    const [fps, setFps] = useState<number | null>(null);
    const [latencyMs, setLatencyMs] = useState<number | null>(null);

    useEffect(() => {
        if (!pc || !pc.webrtcUrl || !pc.streamName) {
            setVideoStatus("idle");
            setSignallingUrl(null);
            setVideoError("");
            setFps(null);
            setLatencyMs(null);
            return;
        }

        let disposed = false;

        let peerConnection: RTCPeerConnection | null = null;
        let signalSocket: WebSocket | null = null;

        let listRetryTimer: number | null = null;
        let sessionRetryTimer: number | null = null;
        let reconnectTimer: number | null = null;
        let fpsTimer: number | null = null;

        let currentSessionId: string | null = null;
        let targetProducerPeerId: string | null = null;
        let offerReceived = false;
        let lastTotalFrames = 0;

        const streamName = pc.streamName;
        const nextSignallingUrl = buildSignallingUrl(pc.webrtcUrl);

        setSignallingUrl(nextSignallingUrl);
        setVideoError("");
        setVideoStatus("connecting");
        setFps(null);
        setLatencyMs(null);

        function safeSend(payload: unknown) {
            if (!signalSocket || signalSocket.readyState !== WebSocket.OPEN) {
                return;
            }

            signalSocket.send(JSON.stringify(payload));
        }

        function stopProducerDiscoveryLoop() {
            if (listRetryTimer !== null) {
                window.clearInterval(listRetryTimer);
                listRetryTimer = null;
            }
        }

        function stopSessionRetryLoop() {
            if (sessionRetryTimer !== null) {
                window.clearInterval(sessionRetryTimer);
                sessionRetryTimer = null;
            }
        }

        function stopFpsCounter() {
            if (fpsTimer !== null) {
                window.clearInterval(fpsTimer);
                fpsTimer = null;
            }

            setFps(null);
            setLatencyMs(null);
            lastTotalFrames = 0;
        }

        function startProducerDiscoveryLoop() {
            stopProducerDiscoveryLoop();

            listRetryTimer = window.setInterval(() => {
                if (!signalSocket || signalSocket.readyState !== WebSocket.OPEN) {
                    return;
                }

                if (targetProducerPeerId) {
                    return;
                }

                safeSend({ type: "list" });
            }, 1500);
        }

        function startSessionRetryLoop() {
            stopSessionRetryLoop();

            sessionRetryTimer = window.setInterval(() => {
                if (!signalSocket || signalSocket.readyState !== WebSocket.OPEN) {
                    return;
                }

                if (!targetProducerPeerId) {
                    return;
                }

                if (currentSessionId || offerReceived) {
                    return;
                }

                safeSend({
                    type: "startSession",
                    peerId: targetProducerPeerId,
                });
            }, 2000);
        }

        function startFpsCounter() {
            if (fpsTimer !== null) {
                return;
            }

            const video = videoRef.current;

            if (!video) {
                return;
            }

            try {
                lastTotalFrames = video.getVideoPlaybackQuality().totalVideoFrames;
            } catch {
                lastTotalFrames = 0;
            }

            fpsTimer = window.setInterval(() => {
                const currentVideo = videoRef.current;

                if (!currentVideo) {
                    return;
                }

                void collectVideoMetrics(currentVideo);
            }, 1000);
        }

        async function collectVideoMetrics(currentVideo: HTMLVideoElement) {
            let nextFps: number | null = null;
            let nextLatencyMs: number | null = null;

            try {
                const quality = currentVideo.getVideoPlaybackQuality();
                const currentFrames = quality.totalVideoFrames;

                nextFps = Math.max(0, currentFrames - lastTotalFrames);
                lastTotalFrames = currentFrames;
                setFps(nextFps);
            } catch {
                setFps(null);
            }

            try {
                if (peerConnection) {
                    const stats = await peerConnection.getStats();

                    stats.forEach((report) => {
                        const candidatePair = report as RTCIceCandidatePairStats & {
                            selected?: boolean;
                        };

                        if (
                            report.type === "candidate-pair" &&
                            (candidatePair.selected === true || candidatePair.state === "succeeded") &&
                            typeof candidatePair.currentRoundTripTime === "number" &&
                            candidatePair.currentRoundTripTime > 0
                        ) {
                            nextLatencyMs = Math.round(candidatePair.currentRoundTripTime * 1000);
                        }

                        const remoteInbound = report as RTCRemoteInboundRtpStreamStats;

                        if (
                            report.type === "remote-inbound-rtp" &&
                            typeof remoteInbound.roundTripTime === "number" &&
                            remoteInbound.roundTripTime > 0
                        ) {
                            nextLatencyMs = Math.round(remoteInbound.roundTripTime * 1000);
                        }
                    });
                }
            } catch {
                nextLatencyMs = null;
            }

            setLatencyMs(nextLatencyMs);

            window.dispatchEvent(
                new CustomEvent("remote-video-metrics", {
                    detail: {
                        pcId,
                        pcName,
                        fps: nextFps,
                        latency: nextLatencyMs,
                    },
                }),
            );
        }

        function cleanupCurrentConnection() {
            stopProducerDiscoveryLoop();
            stopSessionRetryLoop();
            stopFpsCounter();

            if (signalSocket) {
                signalSocket.onopen = null;
                signalSocket.onmessage = null;
                signalSocket.onerror = null;
                signalSocket.onclose = null;

                try {
                    signalSocket.close();
                } catch {
                    // ignore
                }

                signalSocket = null;
            }

            if (peerConnection) {
                peerConnection.ontrack = null;
                peerConnection.onicecandidate = null;
                peerConnection.onconnectionstatechange = null;

                try {
                    peerConnection.close();
                } catch {
                    // ignore
                }

                peerConnection = null;
            }

            const video = videoRef.current;

            if (video) {
                video.srcObject = null;
            }

            currentSessionId = null;
            targetProducerPeerId = null;
            offerReceived = false;
        }

        function scheduleReconnect(message?: string) {
            if (disposed) {
                return;
            }

            if (reconnectTimer !== null) {
                return;
            }

            cleanupCurrentConnection();

            if (message) {
                setVideoError(message);
            }

            setVideoStatus("reconnecting");

            reconnectTimer = window.setTimeout(() => {
                reconnectTimer = null;

                if (!disposed) {
                    connectVideo();
                }
            }, 1800);
        }

        async function handleSignalMessage(data: SignalMessage) {
            if (disposed) {
                return;
            }

            if (data.type === "welcome" && data.peerId) {
                safeSend({
                    type: "setPeerStatus",
                    roles: ["listener", "consumer"],
                    meta: {
                        name: "watch-client",
                        pcId: String(pcId),
                        pcName,
                        platform: navigator.platform,
                        browser: navigator.userAgent,
                    },
                });

                safeSend({ type: "list" });
                startProducerDiscoveryLoop();
                setVideoStatus("producer_search");
                return;
            }

            if (data.type === "list" && Array.isArray(data.producers)) {
                const producer = data.producers.find((item) => {
                    const meta = item.meta || {};

                    return (
                        meta.name === streamName ||
                        item.id === streamName ||
                        item.peerId === streamName
                    );
                });

                if (!producer) {
                    setVideoStatus("producer_search");
                    return;
                }

                targetProducerPeerId = producer.peerId || producer.id || null;

                if (!targetProducerPeerId) {
                    setVideoError("Producer найден, но peerId отсутствует");
                    setVideoStatus("error");
                    return;
                }

                stopProducerDiscoveryLoop();

                safeSend({
                    type: "startSession",
                    peerId: targetProducerPeerId,
                });

                startSessionRetryLoop();
                setVideoStatus("session_starting");
                return;
            }

            if (data.type === "sessionStarted") {
                currentSessionId = data.sessionId || null;
                stopSessionRetryLoop();
                return;
            }

            if (data.type === "peer") {
                if (data.sessionId) {
                    currentSessionId = data.sessionId;
                }

                if (data.sdp && data.sdp.type === "offer") {
                    if (!peerConnection) {
                        return;
                    }

                    offerReceived = true;
                    stopSessionRetryLoop();
                    setVideoStatus("offer_received");

                    await peerConnection.setRemoteDescription(
                        new RTCSessionDescription({
                            type: "offer",
                            sdp: data.sdp.sdp,
                        }),
                    );

                    const answer = await peerConnection.createAnswer();
                    await peerConnection.setLocalDescription(answer);

                    safeSend({
                        type: "peer",
                        sessionId: currentSessionId,
                        sdp: {
                            type: "answer",
                            sdp: answer.sdp,
                        },
                    });

                    return;
                }

                if (data.ice && typeof data.ice.sdpMLineIndex !== "undefined") {
                    const candidate = data.ice.candidate;

                    if (!candidate || candidate.trim() === "") {
                        return;
                    }

                    try {
                        await peerConnection?.addIceCandidate(
                            new RTCIceCandidate({
                                candidate,
                                sdpMLineIndex: data.ice.sdpMLineIndex,
                            }),
                        );
                    } catch (e) {
                        console.warn("ICE add failed:", e);
                    }

                    return;
                }
            }

            if (data.type === "endSession") {
                scheduleReconnect("Трансляция переподключается...");
                return;
            }

            if (data.type === "error") {
                setVideoError(data.message || "Ошибка GStreamer signalling");
                setVideoStatus("error");
            }
        }

        function connectVideo() {
            if (disposed) {
                return;
            }

            cleanupCurrentConnection();

            setVideoStatus("connecting");
            setVideoError("");

            peerConnection = new RTCPeerConnection({
                iceServers: [
                    {
                        urls: "stun:stun.l.google.com:19302",
                    },
                ],
            });

            peerConnection.ontrack = async (event) => {
                const video = videoRef.current;

                if (!video) {
                    return;
                }

                const stream =
                    event.streams && event.streams.length > 0
                        ? event.streams[0]
                        : new MediaStream([event.track]);

                video.srcObject = stream;
                video.muted = true;
                video.autoplay = true;
                video.playsInline = true;

                setVideoStatus("track_received");

                try {
                    await video.play();
                    setVideoError("");
                    setVideoStatus("playing");
                    startFpsCounter();
                } catch {
                    setVideoError("Браузер заблокировал автоматическое воспроизведение");
                    setVideoStatus("error");
                }
            };

            peerConnection.onicecandidate = (event) => {
                if (!signalSocket || signalSocket.readyState !== WebSocket.OPEN) {
                    return;
                }

                if (!currentSessionId) {
                    return;
                }

                safeSend({
                    type: "peer",
                    sessionId: currentSessionId,
                    ice: event.candidate
                        ? {
                            candidate: event.candidate.candidate,
                            sdpMLineIndex: event.candidate.sdpMLineIndex,
                        }
                        : {
                            candidate: "",
                            sdpMLineIndex: 0,
                        },
                });
            };

            peerConnection.onconnectionstatechange = () => {
                if (!peerConnection) {
                    return;
                }

                const state = peerConnection.connectionState;

                if (state === "connected") {
                    setVideoError("");
                    setVideoStatus("connected");
                }

                if (state === "failed" || state === "disconnected") {
                    scheduleReconnect("Трансляция переподключается...");
                }
            };

            signalSocket = new WebSocket(nextSignallingUrl);

            signalSocket.onopen = () => {
                setVideoStatus("signalling_connected");
            };

            signalSocket.onmessage = async (message) => {
                try {
                    const data = JSON.parse(message.data) as SignalMessage;
                    await handleSignalMessage(data);
                } catch (e) {
                    console.error("Signalling parse/handle error:", e, message.data);
                    scheduleReconnect("Ошибка обработки GStreamer signalling");
                }
            };

            signalSocket.onerror = () => {
                scheduleReconnect("Ожидание GStreamer signalling...");
            };

            signalSocket.onclose = () => {
                if (!disposed) {
                    scheduleReconnect("Ожидание GStreamer signalling...");
                }
            };
        }

        connectVideo();

        return () => {
            disposed = true;

            if (reconnectTimer !== null) {
                window.clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }

            cleanupCurrentConnection();
        };
    }, [pc, pcId, pcName]);

    return {
        videoRef,
        videoStatus,
        signallingUrl,
        videoError,
        fps,
        latencyMs,
    };
}

function buildSignallingUrl(rawWebrtcUrl: string): string {
    const fixedUrl = fixLocalhostUrl(rawWebrtcUrl);
    const url = new URL(fixedUrl);
    const protocol = url.protocol === "https:" ? "wss:" : "ws:";

    return `${protocol}//${url.hostname}:8443`;
}

function fixLocalhostUrl(rawUrl: string): string {
    const url = new URL(rawUrl);

    if (url.hostname === "localhost" || url.hostname === "127.0.0.1") {
        url.hostname = window.location.hostname;
    }

    return url.toString();
}