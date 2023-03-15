FOSS Reporting
==============
Generate various reports regarding the FOSS licenses used in a project.

The foss-report tool works on license report generated on the repositories of a project.
foss-report collects the license reports from all repositories in a given folder and aggregates the artifact and license information in a single collection.
For this collection reports can be generated and the licenses and source artifacts can be downloaded.
The foss-report can also generate diff report on two versions of the repositories or reports.

Currently THIRD-PARTY.txt reports generated with Apache Maven are supported.

As there is no standard way of naming specific licenses in project files, the foss-report tool uses a configuration file named 'txt2spdx.json'. It contains the mapping of the license names as provided by the project (e.g. in the pom.xml) to the SPDX id of the license. The SPDX id of the license can be used to get further information for the license, like the license text or OSS/FSF compatibility.




Build
-----
The foss-tool uses [leiningen](https://leiningen.org/) as a build tool.
With leiningen installed, you can generate a jar containing all the dependencies with
```
lein uberjar
```


Usage
-----
You can generate the Thirdparty file from the directory of a maven project with th maven license plugin, e.g.
```
mvn license:add-third-party -Dlicense.excludedScopes=test 
```

If you have a directory with multiple maven projects, you can use the following bash command to generate the Thirdparty files for all projects, e.g.
```
find git -maxdepth 3 -type f -name pom.xml -execdir mvn license:add-third-party -Dlicense.excludedScopes=test \;
```


You can run the foss-tool from project directory with [leiningen](https://leiningen.org/):
```
lein run [options]
```

You can also run the uberjar with java:
```
java -jar <path-to-jar>\foss-report.jar [options]
```

Options
```
  -b, --base-dir DIRNAME                     Base directory. If set, the paths are relative to this directory.
  -c, --current FILENAME                     Name of the file or directory with current artifact information.
  -p, --previous FILENAME                    Name of the file or directory with current artifact information.
  -i, --spdx-file FILENAME    txt2spdx.json  Name of the license names to SPDX ID mapping file.
  -r, --foss-report                          Generate a FOSS report.
  -d, --foss-diff                            Generate a FOSS diff report. Needs current and previous input.
  -u, --update-spdx-mapping                  Generate an updated license names to SPDX ID mapping.
  -l, --download-licenses                    Download the relevant licenses from SPDX.
  -L, --licenses-dir DIRNAME  licenses       Directory for the download of licenses from SPDX.
  -s, --download-sources                     Download the relevant source jars.
  -S, --sources-dir DIRNAME   sources        Directory for the download of source jars.
  -y, --scan-sources                         Scan the sources for copyrights and notices.
  -f, --report-format FORMAT  xls            The output format for reports (xls, json, stdout).
  -v, --versions                             Process each artifact version.
  -g, --gpl-only                             Reports the list of GPL only licensed artifacts.
  -n, --no-foss-license                      Reports the list of artifacts without FOSS license.
  -h, --help                                 Print usage information.
```


Problems
--------

Errors accessing spdx.org (e.g. Exception in thread "main" java.io.FileNotFoundException: https://spdx.org/licenses/CC0.json)
-> Edit the txt2spdx.json file to fix the missing entry.


Problems with current maven plugins
-----------------------------------
* POM information
  * license information in the POM not normed, unreliable or uncomplete
* Maven plugins
  * generated reports are not machine readable
