# Yaşlı Telefon Asistanı (BST401 Bitirme Projesi)

Android-only assistant helping elderly users use their phones (floating bubble + voice; "Do-it" mode and "Guide" mode). Vision: `docs/PROJE-VIZYONU.md`. Requirements (source of truth): `docs/GEREKSINIMLER.md`. Plan: `docs/PLAN.md`. Progress: `docs/ILERLEME.md`.

## Rules
- Talk to the user in Turkish. The user is a student with limited technical background: explain plainly.
- Zero budget: only free tools/tiers; no paid APIs, no time-limited trials. Flag anything that would cost money.
- Treat the app as if published on Play Store: KVKK compliance and privacy are mandatory.
- Nothing may contradict the professor's feedback, the student's documents or the papers. Precedence: professor > student > papers.

## Orchestration (token discipline: Pro plan limits)
- The main session (Opus 5.5; was Fable until 18.11.2026) coordinates only: it never reads raw papers or writes large code itself.
- EXCEPTION (user decision 18.11.2026, demo deadline): after the running P-A/P-D packages finish, the main session writes code itself (no new gelistirici subagents) until the demo scope in `docs/ILERLEME.md` is done.
- `makale-okuyucu` (Haiku): paper extraction. `uyum-analisti` (Sonnet): requirements synthesis + compliance review at milestones. `gelistirici` (Opus): one plan task per call.
- Subagents write results to files and return short summaries. Read files only when a decision needs them.
- After each completed task, update `docs/ILERLEME.md` (done / next / blockers) so work resumes cleanly after a limit reset. On "devam", read `docs/ILERLEME.md` first.

## Layout
- `makaleler/`: original PDFs (do not read directly; use `docs/arastirma/metin/*.txt`)
- `docs/arastirma/notlar/`: one note per paper
- `docs/arastirma/kaynaklar/`: student documents + professor feedback as text/images
