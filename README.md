# authlib-patcher

[![License: LGPL v3](https://img.shields.io/badge/License-LGPL%20v3-blue.svg)](https://www.gnu.org/licenses/lgpl-3.0)

## WHAT'S THIS?
This build script apply patches for Mojang's Minecraft authentication library authlib.

## OBJECTIVE AND RESULT
#### OBJECTIVE
* So that we can implement protocol translator which send skin data for PC users from BE servers.

#### RESULT
* Skip skin url signature verification
* Allow data scheme url for skin data

## BRIEF HISTORY
When I was developping a PMMP plugin BigBrother which enable PC users to login to the BE server,
I found some problem to convert player's skin data for PC users.

In Minecraft PC Edition, user's skin data is managed by Mojang's server,
and the skin url is signed by Mojang's Yggdrasil session server's private key.
We can obtain a public key from original authlib
but it is hard to crack because the key-length is also 4096bit.

In addition the url must ends with '.minecraft.net' or '.mojang.com' because the
whitelist domain is hard-coded in the authlib.

So, I decided to make some patches for authlib to bypass signature verification,
and support data scheme url handler to pass users skin data as data url.

## HOW THIS SCRIPT WORKS
This gradle script is work in this way.

* Extract resources from the original authlib-1.5.25.jar to the directory src/main/resources
* Decompile the original authlib-1.5.25.jar to the directory src/main/java
* Apply all patches in the patch/ directory
* Recompile all java source files in the directory src/main/java
* Assemble jar files with compiled classes and resources files under the directory src/main/resources


## ⚠ WARNING
This project it-self not containing any proprietary codes or resources,
but when you run this build script, extracted resources and
decompiled source codes are generated under the directory `src/main`.

And generated `authlib-1.5.25.jar` file also contains those resources or
compiled classes from the decompiled source codes.

**YOU MUST NOT REDISTRIBUTE EXTRACTED RESOURCES OR DECOMPILED SOURCE CODES OR GENERATED `authlib-1.5.25.jar`
PLEASE USE THIS SOFTWRE AT YOUR OWN RISK**

## PREREQUISITE
* You must have legal copy of minecraft PC (Java) version
* You should run minecraft version 1.12.2 at least once
* Install Oracle Java SE Development Kit 8+ (JDK 1.8+) or OpenJDK 1.8+
* Install Gradle Build Tool


## HOW TO BUILD

Just type the command follows.

```bash
user@locahost: ~/work/authlib$ gradle build
```

From now on, `authlib-1.5.25.jar` is generated under `build/libs` directory.
