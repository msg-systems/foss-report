FOSS Reporting
==============
Generate various reports regarding the FOSS licenses used in a project.

Usage
-----
Run from project directory with [leiningen](https://leiningen.org/):
```
lein run [options]
```

Run the uberjar with java:
```
java -jar <path-to-jar>\foss-report.jar [options]
```

Options
```
  -b, --base-dir DIRNAME                                  Base directory. If set, the paths are relative to this directory.
  -m, --maven-dir DIRNAME        data                     Directory of the maven projects
  -x, --excel-file FILENAME      data/FOSS.xlsm           Name of the excel file with FOSS dependency information
  -t, --excel-template FILENAME  data/FOSS_Template.xlsm  Name of the excel template for FOSS dependency information
  -i, --spdx-file FILENAME       data/txt2spdx.json       Name of the license names to SPDX ID mapping file
  -r, --foss-report                                       Generate a FOSS report
  -d, --foss-diff                                         Generate a FOSS diff report
  -u, --update-spdx-mapping                               Generate an updated license names to SPDX ID mapping
  -l, --download-licenses                                 Download the relevant licenses from SPDX
  -L, --licenses-dir DIRNAME     data/licenses            Directory for the download of licenses from SPDX
  -f, --report-format FORMAT     xls                      The output format for reports (xls, json, stdout)
  -v, --versions                                          Process each artifact version
  -h, --help                                              Print usage information
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
