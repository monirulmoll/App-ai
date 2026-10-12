# Gemo AI — Autonomous AI Backend Engine & Architecture

**Version:** 5.0.0-PROD  
**Author & Creator:** Rohit  
**Target Clients:** Gemo AI Android Application & Autonomous Termux/Linux Environments  

---

## 1. Quick Start

Run the environment check:
```bash
python Setup.py
```

Start the autonomous backend engine:
```bash
python main.py
```

Or pass a custom Firebase Realtime Database URL:
```bash
python main.py --firebase-url "https://your-database-id-default-rtdb.firebaseio.com"
```

---

## 2. Architecture & Single-Root Firebase Schema

The Firebase Realtime Database strictly follows the **Single-Root Architecture**:
```text
/
└── users/
    └── {username}/
        ├── profile/
        │   ├── username: string
        │   ├── canonicalUsername: string
        │   ├── createdAt: number
        │   └── updatedAt: number
        ├── auth/
        │   ├── passwordHash: string (PBKDF2/SHA-256)
        │   ├── accountStatus: "active"
        │   └── sessions/
        ├── settings/
        │   ├── model_name: string
        │   ├── provider: string
        │   ├── system_instruction: string
        │   └── ...
        ├── memory/
        │   └── active/
        ├── workspace/
        │   └── files/
        │       └── {sanitized_filename}/
        ├── agent/
        │   └── terminal/
        │       ├── commands/
        │       └── output/
        ├── stop/
        ├── stopGeneration: boolean
        └── conversations/
            └── {conversationId}/
                ├── id: string
                ├── title: string
                ├── userId: string
                ├── model: string
                ├── maker: "Rohit"
                ├── updatedAt: number
                └── messages/
                    ├── {userMessageId}/
                    │   ├── messageId: string
                    │   ├── conversationId: string
                    │   ├── sender: "user"
                    │   ├── userId: string
                    │   ├── text: string
                    │   ├── requestId: string
                    │   ├── status: "sent" | "generating" | "completed"
                    │   └── timestamp: number
                    └── {aiMessageId}/
                        ├── messageId: string
                        ├── conversationId: string
                        ├── sender: "ai"
                        ├── userId: string
                        ├── text: string
                        ├── requestId: string
                        ├── model: string
                        ├── maker: "Rohit"
                        ├── status: "completed"
                        └── timestamp: number
```

---

## 3. Backend Structure & Modules

- **`main.py`**: Clean entry point for launching the engine (`python main.py`).
- **`backend_config.json`**: Central configuration file defining Firebase endpoints, polling rates, timeouts, and schema node names.
- **`config.py`**: Configuration manager that loads from JSON, environment variables, or CLI arguments.
- **`logger.py`**: Stylish terminal logger displaying organized ANSI banners, badges, and real-time processing sections.
- **`Autonomous.py`**: Core autonomous engine handling multi-user conversation dispatching, code generation, terminal execution, workspace file sync, and stop signal isolation.
- **`Setup.py`**: Zero-dependency environment verification script.
- **`FIREBASE_RTDB_SCHEMA_AND_BACKEND_CONTRACT.md`**: Authoritative system specification and contract.
