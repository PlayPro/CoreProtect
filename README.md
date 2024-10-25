![CoreProtect](https://userfolio.com/uploads/coreprotect-banner-v19.png)

[![Artistic License 2.0](https://img.shields.io/github/license/PlayPro/CoreProtect?&logo=github)](LICENSE)
[![GitHub Workflows](https://github.com/PlayPro/CoreProtect/actions/workflows/build.yml/badge.svg)](https://github.com/PlayPro/CoreProtect/actions)
[![Netlify Status](https://img.shields.io/netlify/c1d26a0f-65c5-4e4b-95d7-e08af671ab67)](https://app.netlify.com/sites/coreprotect/deploys)
[![CodeFactor](https://www.codefactor.io/repository/github/playpro/coreprotect/badge)](https://www.codefactor.io/repository/github/playpro/coreprotect)
[![Join us on Discord](https://img.shields.io/discord/348680641560313868.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/b4DZ4jy)

## Modification of CoreProtect for Circle of Imagination

Development
------

1. Clone repository to your local machine
2. Open, load both Gradle and Maven projects in your IDE
3. Run `gradle build` to build the project. Probably will fail due to missing dependencies.
4. Run `mvn clean install` to install to your local maven repository.
5. Run `mvn package` to build the plugin jar file. Will be located in `target/CoreProtect-22.4.{version}.jar`
6. Define local maven repository in your IDE to use the local maven repository for the dependencies: `~/.m2/repository`
7. Add this to your build.gradle: `compileOnly('net.coreprotect:CoreProtect:22.4.{version}')`

Modifications
-----
1. Custom method for abilities logging. Works well with `/co lookup action:ability`
2. Custom method for logging block changes brought by abilities. Works well with `/co lookup action:ability-block`
3. Custom changes by abilities have identifier and player name, as well as the ability name. 
4. Custom changes by abilities are rollbackable. Batch size for saving is zero, each changes is saved separately.

JitPack
------

1. Add the JitPack repository to your build.gradle:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```

2. Add the dependency to your build.gradle:

```gradle
dependencies {
    compileOnly 'com.github.mc-uaproject:uaproject-core-protect:v22.4.{version}'
}
```

3. Replace `{version}` with the version you want to use. Some versions may not be available as modifications are made to the project.

Contributing
------
CoreProtect is an open source project, and gladly accepts community contributions.

If you'd like to contribute, please read our contributing guidelines here: [CONTRIBUTING.md](CONTRIBUTING.md)

[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-2.0-4baaaa.svg)](CONTRIBUTING.md#code-of-conduct) 