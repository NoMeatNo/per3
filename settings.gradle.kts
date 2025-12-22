rootProject.name = "CloudstreamPlugins"

// This file sets what projects are included. All new projects should get automatically included unless specified in "disabled" variable.

// Only build FarsiFlixModularProvider for now
include("FarsiFlixModularProvider")

// Comment out the auto-include to disable all other providers:
// val disabled = listOf<String>(
//     "EinthusanProvider"
// )

// File(rootDir, ".").eachDir { dir ->
//     if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
//         include(dir.name)
//     }
// }

// fun File.eachDir(block: (File) -> Unit) {
//     listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
// }
