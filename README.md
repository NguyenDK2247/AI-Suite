# 🤖🌤️💱 AI Agent and Interface 

A personal, work-in-progress (WIP) AI agent and web interface designed to execute automated tasks and broadcast information under direct command. 

Currently, the agent is capable of processing weather and currency related prompts and broadcasting real-time updates. The project is actively undergoing UI enhancements, layout expansions, and visual styling updates.

## 🚀 Current Features
* **Weather Broadcast Agent:** processes user commands and prompts to fetch and broadcast current weather conditions
* **Currency Exchange Agent:** processes user commands and prompts to fetch and broadcast current currency exchange rates
* **Interactive UI:** a dedicated web interface to communicate with and monitor the AI agent

## 🛠️ Tech Stack (WIP)
* **Frontend:** React / Next.js + Tailwind CSS (WIP)
* **Styling:** Tailwind CSS / CSS Modules
* **Agent Logic:** LangChain4j (WIP)

## 🚧 Roadmaps & Upcoming Enhancements
- [ ] **UI/UX Redesign:** extend the layout to support multi-column dashboards
- [ ] **Visual Decoration:** implement dark mode, modern animations, and polished UI components
- [ ] **Agent Capability Expansion:** add more command modules beyond weather (e.g., daily schedules, news summaries, or system automation)
- [ ] **Persistent Memory:** allow the agent to remember user preferences across sessions
- [ ] **Retrieved-Augmented Generation (RAG) and Vector Database Integration:** implement [RAG/Vector Database Integration] to allow the agent to [fetch/store and retrieve] relevant information from external sources and use it to generate responses
- [ ] **Semantic Search:** uses AI and NLP to understand the meaning and intent behind a user's query

## 📦 Getting Started

### Prerequisites (WIP)
N/A

### Installation
1. Clone the repository:
   ```bash
   git clone [https://github.com/NguyenDK2247/AI-Agent-and-Interface.git](https://github.com/NguyenDK2247/AI-Agent-and-Interface.git)
   cd AI-Agent-and-Interface

### Projected Blueprint
```
[ Frontend: Next.js + Tailwind ] 
             │  ▲
   REST /    │  │ Server-Sent Events (For live text streaming)
   WebSockets▼  │
[ Backend: Spring Boot / Java ] ───► [ Agent Logic: LangChain4j ] ───► [ Weather API ]
```
