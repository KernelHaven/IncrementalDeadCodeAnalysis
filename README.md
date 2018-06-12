# IncrementalDeadCodeAnalysis

This is an analysis-plugin for the [IncrementalAnalysesInfrastructure](https://github.com/KernelHaven/IncrementalAnalysesInfrastructure) performing the DeadCodeAnalysis. This analysis does not work without the incremental infrastructure plugin.

Similar to UndeadAnalyzer, two variants of the analysis exist (a single-threaded and a multithreaded version)


## Usage

Place the jar-file downloaded from the releases section of this project into the plugins folder of KernelHaven.

`analysis.class` can be set to one of (depending on whether you want to run a multi-threaded or single-threaded version)

``
net.ssehub.kernel_haven.incremental.analysis.IncrementalDeadCodeAnalysis
net.ssehub.kernel_haven.incremental.analysis.IncrementalThreadedDeadCodeAnalysis
``

for the multithreaded-version, you can change the number of threads by including the folling line in your KernelHaven-configuration file:

``
analysis.undead.threads = 20
``

## Dependencies

In addition to KernelHaven, this plugin has the following dependencies:
* [CnfUtils](https://github.com/KernelHaven/CnfUtils)
* [IncrementalAnalysesInfrastructure](https://github.com/KernelHaven/IncrementalAnalysesInfrastructure)
* [UndeadAnalyzer](https://github.com/KernelHaven/UnDeadAnalyzer)

## License

This plugin is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).
