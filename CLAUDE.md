# CLAUDE.md - CoreProtect

## Project Overview

CoreProtect is a Minecraft server plugin (Bukkit/Paper) that provides data logging and anti-griefing protection. It tracks block changes, player actions, and server events to help prevent and investigate griefing.

- **Version**: 23.1
- **Java**: 11+ (compile target: 11)
- **Minecraft**: 1.14 - 1.21
- **Build System**: Maven
- **Main Class**: `net.coreprotect.CoreProtect`

## Build Commands

```bash
# Build the plugin JAR
mvn clean package

# Build with tests enabled (tests are skipped by default)
mvn clean package -DskipTests=false

# Run tests only
mvn test -DskipTests=false

# Full verification build (used in CI)
mvn -B verify
```

**Output**: `target/CoreProtect-23.1.jar`

## Project Structure

```
src/main/java/net/coreprotect/
├── CoreProtect.java              # Plugin entry point
├── CoreProtectAPI.java           # Public API (v11)
├── api/                          # API classes
├── bukkit/                       # Bukkit compatibility
├── command/                      # Command handlers (/co, /core, /coreprotect)
│   ├── lookup/                   # Lookup command threads
│   └── parser/                   # Argument parsers
├── config/                       # Configuration handling
├── consumer/                     # Async queue processing for logging
├── database/                     # Database layer (SQLite/MySQL)
│   ├── logger/                   # Event loggers
│   ├── lookup/                   # Query implementations
│   ├── rollback/                 # Rollback/restore logic
│   └── statement/                # SQL builders
├── listener/                     # Bukkit event listeners (60+)
│   ├── block/                    # Block events
│   ├── entity/                   # Entity events
│   ├── player/                   # Player events
│   │   └── inspector/            # Block inspection tool
│   └── world/                    # World events
├── language/                     # i18n (15 languages)
├── paper/                        # Paper-specific adapters
├── patch/                        # DB schema migrations
├── spigot/                       # Spigot compatibility
└── utility/                      # Utility classes
```

## Key Entry Points

- **Plugin Main**: `src/main/java/net/coreprotect/CoreProtect.java`
- **Public API**: `src/main/java/net/coreprotect/CoreProtectAPI.java`
- **Plugin Manifest**: `src/main/resources/plugin.yml`
- **Configuration**: `src/main/java/net/coreprotect/config/Config.java`
- **Database Core**: `src/main/java/net/coreprotect/database/Database.java`

## Code Style Guidelines

- **Indentation**: Spaces only (no tabs)
- **Naming**: `descriptiveCamelCase` - avoid underscores and abbreviations
- **Commits**: Single event or section of code per commit
- **PRs**: Keep modifications small and readable
- **Analysis**: Use SonarLint for code quality

## Testing

- **Framework**: JUnit 5 with Mockito
- **Mock Server**: MockBukkit for Bukkit server simulation
- **Database**: SQLite JDBC for test database
- **Location**: `src/test/java/net/coreprotect/`

Tests are skipped by default. Enable with `-DskipTests=false`.

## Dependencies

**Runtime** (shaded into JAR):
- HikariCP 5.0.1 - Database connection pooling
- bStats 3.0.2 - Anonymous usage statistics

**Provided** (by server):
- Paper API 1.21.11
- FastAsyncWorldEdit 2.13.1 (optional)
- AdvancedChests API (optional)

## Commands

The plugin registers three command aliases:
- `/co` - Primary command (default enabled)
- `/core` - Alternative
- `/coreprotect` - Full name

## Database Support

- SQLite (default, file-based)
- MySQL (via HikariCP connection pool)

Schema migrations handled in `database/patch/` directory.

## Important Patterns

1. **Consumer Queue**: All logging is async via consumer queue (`consumer/` package)
2. **Database Abstraction**: SQL statements built via `database/statement/` classes
3. **Event Listeners**: Extensive listener coverage in `listener/` (60+ listeners)
4. **Rollback System**: Block-by-block restoration in `database/rollback/`
5. **Inspector Tool**: Click-based block history in `listener/player/inspector/`

## Integrations

- FastAsyncWorldEdit - Block logging integration
- AdvancedChests - Custom chest support
- WorldEdit - Edit session logging
