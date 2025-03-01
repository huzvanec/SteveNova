plugins {
    `maven-publish`
    `version-catalog`
}

catalog {
    versionCatalog {
        version("kotlin", libs.versions.kotlin.get())
        version("paper", libs.versions.paper.get())
        version("paperweight", libs.versions.paperweight.get())
        version("nova", project.version.toString())

        plugin("kotlin", "org.jetbrains.kotlin.jvm").versionRef("kotlin")
        plugin("paperweight", "io.papermc.paperweight.userdev").versionRef("paperweight")
        plugin("nova", "cz.jeme.stevenova.nova-gradle-plugin").versionRef("nova")

        library("nova", "cz.jeme.stevenova", "nova").versionRef("nova")
    }
}

publishing {
    repositories {
        maven {
            credentials {
                name = "xenondevs"
                url = uri { "https://repo.xenondevs.xyz/releases/" }
                credentials(PasswordCredentials::class)
            }
        }
    }

    publications {
        create<MavenPublication>("catalog") {
            from(components["versionCatalog"])
        }
    }
}