# 15 Agent Workflow Memory (Wang 2024)
**Type:** system / method (benchmark evaluation on WebArena and Mind2Web) · **Relevance:** high

Note: the file name says 2025; the paper's arXiv version (arXiv:2409.07429v1) is dated 2024, so this note uses 2024.

## Core contribution (2-3 lines)
Agent Workflow Memory (AWM) induces reusable routines (workflows) from past agent trajectories, either offline from annotated examples or online from the agent's own successful runs. It stores them in memory (or as callable actions) to guide later tasks. Gains are shown on web navigation without human-written workflows.

## Key findings (bullets, with numbers)
- WebArena (GPT-4): AWM 35.5% total success vs 23.5% BrowserGym baseline (+12.0 absolute, +51.1% relative); about 2.0 fewer steps than BrowserGym.
- Mind2Web cross-task (GPT-4): step success rate 45.1 (offline AWM) vs 36.2 for MindAct (+8.9). Cross-domain step SR 32.6 (offline) and 35.5 (online) vs 18.6.
- Online learning: most of the gain appears in the first 0-40 examples, then performance plateaus.
- Workflows compose: "find place by name" became a sub-step of "get zip code of a place".
- AWMAS (workflows as callable actions) was used in only 18.5% of tasks; gain was small.
- Text vs code workflow format: no substantial difference (step SR 45.4 vs 45.1).
- Abstracting example-specific values (e.g., "{product-name}") improved element accuracy by +5.0 over full-example retrieval (Synapse).

## Implications for our app (bullets; tag each)
- [MEMORY] Store successful task routines abstracted over parameters (contact name, photo) so they replay for new instances; this fits "learned tasks replayed without AI".
- [MEMORY] Learn online from each completed task; add a routine only when the run is judged successful.
- [LATENCY] Replaying a stored routine skips LLM calls for known steps; otherwise the agent calls the LLM at every step.
- [SAFETY] Routines can push the agent along a fixed path even when the screen differs; the paper found this fails on dynamic pop-ups, so each step needs a check.
- [EVAL] The LM judge decides which runs enter memory; a wrong judgment stores a wrong routine.
- [ELDERLY] Stored routines can prevent the repeated mistakes older users make.

## Techniques we could reuse (concrete)
- Induction prompt: extract common sub-routines from one or more successful trajectories; abstract example-specific values into placeholders.
- Workflow record = NL description of the goal + steps (environment state description, reasoning, action).
- Online loop: run task, binary success label from an LM judge, induce workflows from successes only, add to memory, use on the next task.
- Compose complexity by reusing earlier workflows as prefixes of new ones.
- Keep a separate memory per app or site to keep it small and relevant.

## Risks / warnings the paper raises
- Workflows can make the agent follow irrelevant steps; action F1 dropped slightly against MindAct.
- Workflows cannot react to dynamic changes (e.g., airport pop-up options in flight booking).
- Offline induction from mismatched training data can degrade results; the online variant avoids this.
- Induced workflows depend on the judge's accuracy.
- Evaluated only on web tasks, with GPT-4 and GPT-3.5 at temperature 0.

## Evaluation ideas (metrics/protocols we could use to evaluate our app)
- Cumulative success rate over the first k completed tasks (learning curve).
- Step success and task success per routine (Mind2Web-style).
- Steps and LLM calls per task before and after memory.
- Cross-template test: same routine, different parameter values.
- How often a stored routine is applied and how often it fails on a changed screen.

## One-line citation
Wang, Z. Z., Mao, J., Fried, D., & Neubig, G. (2024). Agent Workflow Memory. arXiv:2409.07429. https://arxiv.org/abs/2409.07429
