Что заменять:
- src/features/education/educationApi.ts
- src/pages/EducationStudentPage.tsx
- src/pages/EducationTeacherPage.tsx
- src/features/profile/userDisplayName.ts

После замены запустите:
.pply-education-display-name-patch.cmd

Этот дополнительный скрипт нужен, если вход студента выполняется через PcsPage.tsx, где есть локальная функция joinEducationSession.
