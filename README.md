# IncrementalDeadCodeAnalysis

This is an analysis-plugin for the [IncrementalAnalysesInfrastructure](https://github.com/KernelHaven/IncrementalAnalysesInfrastructure) performing the DeadCodeAnalysis. This analysis does not work without the incremental infrastructure plugin.

Similar to UndeadAnalyzer, two variants of the analysis exist (a single-threaded and a multithreaded version)


## Usage

Place the jar-file downloaded from the releases section of this project into the plugins folder of KernelHaven.
As with any incremental analysis, you need to use the IncrementalPreparation as preparation before the main KernelHaven infrastructure is started. You can achieve this by adding the following line to your configuration file

```preparation.class.0 = net.ssehub.kernel_haven.incremental.preparation.IncrementalPreparation```

`analysis.class` can be set to one of (depending on whether you want to run a multi-threaded or single-threaded version)

``
net.ssehub.kernel_haven.incremental.analysis.IncrementalDeadCodeAnalysis
net.ssehub.kernel_haven.incremental.analysis.IncrementalThreadedDeadCodeAnalysis
``

For the multithreaded-version, you can change the number of threads by including the folling line in your KernelHaven-configuration file:

``
analysis.undead.threads = 20
``

## Advanced Configuration

In addition to the parameters of the incremental infrastructure itself, this analysis can be tweaked by the following parameters:

- ```analysis.consider_vm_vars_only ```: This can either be set to true or false. If set to true, the analysis will skip checks for dead code blocks for any block that has no relation to a variability variable (e.g. a linux configuration option defined in KConfig). It thereby constrains the analysis to target only blocks that are related to the variability model.
- ```incremental.analysis.code_model.optimization```: This can either be set to true or false. If set to true, this option takes effect in analyses that run on only a part of the code model (this is possible if build and variability model have not changed) and compares the previous version of the code model for a single code file with the current code model for the same code file. Through this comparison, the analysis can determine whether any of the blocks related to a variability variable have changed. It works by reducing the structure of nested code blocks to blocks that correspond to a variability variable and then determining whether the reduced previous model is the same as the reduced current one.
- ```incremental.analysis.build_model.optimization```: This can either be set to true or false. If set to true, this option is used when the build model changed but the variability model remained the same. After the extraction of the build model, it checks whether the build presence condition for a code file has changed by comparing it against the presence condition from the previous model. If it has not changed, the code file can is skipped in the analysis.




## Dependencies

In addition to KernelHaven, this plugin has the following dependencies:
* [CnfUtils](https://github.com/KernelHaven/CnfUtils)
* [IncrementalAnalysesInfrastructure](https://github.com/KernelHaven/IncrementalAnalysesInfrastructure)
* [UndeadAnalyzer](https://github.com/KernelHaven/UnDeadAnalyzer)

## License

This plugin is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).
