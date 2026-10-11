# GEMO AI: Firebase Realtime Database Architecture & Single-Root Specification

**System Architecture & Integration Specification**  
**Version:** 5.0.0-PROD (Dynamic Single Root `/users/{username}/` & Canonical Schema Cloning)  
**Target Backend Agent:** `Autonomous.py`  
**Target Client:** Gemo AI Android Application  
**Author / Maker:** Rohit  

---

## 1. Core Non-Negotiable Architecture

The Firebase Realtime Database contains **EXACTLY ONE ROOT NODE**:

```text
/
└── users/
```

There are **NO** separate global root nodes such as `auth/`, `agent/`, `model/`, `conversations/`, `workspace/`, `stop/`, `stopGeneration`, or `online`.

Every user's complete data exists inside that user's own dynamically chosen username node under `/users/`:

```text
/users/{username}/
```

- **Canonical Identity**: The account database identity is **strictly the exact username** chosen during registration (trimmed and canonicalized to lowercase as the Firebase key).
- **No Random User IDs or Hardcoded Identities**: Firebase-generated random user IDs or hardcoded placeholder strings are never used as account keys.
- **Strict Data Isolation**: Every user's conversations, messages, workspace files, memory, and settings are strictly isolated under `/users/{username}/`. No user can ever access or receive another user's conversations or data.
- **No Deletion of Existing Data**: Account creation and login are strictly non-destructive and idempotent. Existing user data in the database is never wiped or overwritten.
- **Empty Database Compatibility**: Authentication, registration, and complete structure cloning work reliably even when the database starts completely empty (`/`).

---

## 2. Complete Database Tree Structure

For any registered account `{username}`:

```text
/
└── users/
    └── {username}/
        ├── profile/
        │   ├── username: string (e.g. "{username}")
        │   ├── canonicalUsername: string (e.g. "{username_lowercase}")
        │   ├── createdAt: integer (timestamp ms)
        │   └── updatedAt: integer (timestamp ms)
        ├── auth/
        │   ├── passwordHash: string ("pbkdf2:sha256:600000$...")
        │   ├── accountStatus: string ("active")
        │   ├── createdAt: integer (timestamp ms)
        │   └── sessions/
        │       └── {sessionTokenHash}/
        │           ├── sessionTokenHash: string
        │           ├── createdAt: integer (timestamp ms)
        │           ├── expiresAt: integer (timestamp ms)
        │           └── revoked: boolean
        ├── settings/
        │   ├── model_name: string ("qwen2.5-1.5b-instruct-q4_k_m.gguf")
        │   ├── provider: string ("Qwen")
        │   ├── max_tokens: integer (2048)
        │   ├── temperature: float (0.7)
        │   ├── timeout: integer (45)
        │   ├── response_timeout_seconds: integer (45)
        │   ├── speech_input_language: string ("auto")
        │   ├── system_instruction: string ("You are Gemo AI, an intelligent AI created by Rohit...")
        │   ├── memory_enabled: boolean (true)
        │   ├── translator_enabled: boolean (true)
        │   ├── vision_enabled: boolean (true)
        │   ├── agent_mode_enabled: boolean (false)
        │   └── updated_at: integer (timestamp ms)
        ├── memory/
        │   └── active/
        │       ├── mem_creator/
        │       │   ├── id: string ("mem_creator")
        │       │   ├── category: string ("fact")
        │       │   ├── content: string ("Gemo AI's creator and maker is Rohit.")
        │       │   ├── isEnabled: boolean (true)
        │       │   └── createdAt: integer (timestamp ms)
        │       └── mem_pref_lang/
        │           ├── id: string ("mem_pref_lang")
        │           ├── category: string ("preference")
        │           ├── content: string ("User prefers polite, intelligent, and context-aware responses...")
        │           ├── isEnabled: boolean (true)
        │           └── createdAt: integer (timestamp ms)
        ├── workspace/
        │   └── files/
        │       └── {fileKey}/
        │           ├── filename: string ("main.py")
        │           ├── content: string
        │           ├── sizeBytes: integer
        │           └── lastModified: integer (timestamp ms)
        ├── agent/
        │   ├── status: string ("idle")
        │   ├── lastCommand: string ("")
        │   ├── updatedAt: integer (timestamp ms)
        │   └── terminal/
        │       ├── commands/
        │       │   └── {commandId}/
        │       │       ├── id: string
        │       │       ├── command: string
        │       │       ├── language: string
        │       │       └── status: "PENDING" | "RUNNING" | "COMPLETED"
        │       └── output/
        │           └── {commandId}/
        │               ├── command_id: string
        │               ├── stdout: string
        │               ├── stderr: string
        │               ├── exit_code: integer
        │               └── status: "COMPLETED" | "FAILED"
        ├── stop/
        │   ├── stop: boolean (false)
        │   ├── conversationId: string ("")
        │   ├── requestId: string ("")
        │   └── timestamp: integer (timestamp ms)
        ├── stopGeneration: boolean (false)
        └── conversations/
            └── {conversationId}/
                ├── id: string
                ├── title: string
                ├── userId: string ("{username}")
                ├── model: string
                ├── maker: string ("Rohit")
                ├── createdAt: integer (timestamp ms)
                ├── updatedAt: integer (timestamp ms)
                ├── lastMessage: string
                ├── lastPrompt: string
                ├── prompt: string
                └── messages/
                    └── {messageId}/
                        ├── messageId: string
                        ├── conversationId: string
                        ├── requestId: string
                        ├── sender: "user" | "ai"
                        ├── userId: string ("{username}")
                        ├── text: string
                        ├── status: "sent" | "generating" | "completed" | "error"
                        ├── timestamp: integer (timestamp ms)
                        ├── model: string
                        └── maker: string ("Rohit")
```

---

## 3. Canonical Schema Cloning for Every New Account

Whenever any new account registers:

1. **Exact Dynamic Username**: The username entered by the user is sanitized and verified (length 3–30 chars, alphanumeric + underscores).
2. **Canonical Reference Cloning**: The new account under `/users/{newUsername}/` is initialized with the full canonical structure:
   - Complete `profile` metadata.
   - Secure PBKDF2-SHA256 `auth` node with active session token hash.
   - Complete default `settings` (model, temperature, timeout, Rohit persona system instruction).
   - Core `memory` nodes (`mem_creator` and `mem_pref_lang`).
   - Clean `workspace` and `agent` structures.
   - Safe inactive `stop` and `stopGeneration` state.
3. **No Private Data Copying**: The schema and defaults are cloned, **NEVER** private conversations, messages, or credentials from other accounts.
4. **Idempotence**: If an account already exists or initialization is retried, existing user conversations, settings, workspace files, and memories are never overwritten or deleted.

---

## 4. Strict Security & Zero-Leakage Guarantees

- **User Isolation**: Every database read/write operation is explicitly scoped by the active authenticated user's canonical username (`/users/{username}/...`).
- **Local Cache Isolation**: On the Android client, local preferences, conversation lists, and workspace disk files are scoped per user and wiped from active cache when switching accounts or logging out.
- **Zero Mock / Zero Global State**: No requests ever hit global unpartitioned database keys.
- **Backend Runner Compatibility**: `Autonomous.py` monitors each user's requests exclusively under `/users/{username}/` and posts responses directly to `/users/{username}/conversations/{conversationId}/messages/{messageId}`.
