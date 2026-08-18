package com.remote.pc.model;

/*
 * Временное представление состояния ПК для совместимости
 * с существующим API и текущим frontend.
 *
 * В базе данных этот enum больше не хранится.
 *
 * Реальное состояние разделено на:
 * PcConnectionStatus и PcPowerState.
 */
public enum PcStatus {
    ONLINE,
    OFFLINE,
    SLEEP
}