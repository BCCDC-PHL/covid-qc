# BCCDC-PHL COVID QC Site

This site is used for routine review of quality control data related to SARS-CoV-2 sequencing and genomic analysis.

![site screenshot](doc/images/covid-qc-screenshot.png)

A live demo is available [here](https://bccdc-phl.github.io/covid-qc/). Note that sample identifiers (`ERR...`) correspond to the [NCBI SRA](https://www.ncbi.nlm.nih.gov/sra)
identifiers for the libraries that were used to generate the demo dataset.

## Development Setup

This site is written in [ClojureScript](https://clojurescript.org/), a dialect of [Clojure](https://clojure.org/) that compiles down to JavaScript.  

### Prerequisites - JDK, Clojure & NPM
The ClojureScript compiler requires JDK 8 or later. Follow [this guide](https://clojure.org/guides/getting_started) to installing clojure on your platform before starting development on this site.

The 'Node Package Manager' ([NPM](https://www.npmjs.com/)) is required for installing JavaScript dependencies. Follow [this guide](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm) to install NPM on your platform.

After cloning this repository, run `npm install` from the top-level of the repo to install the JavaScript dependencies into the `node_modules` directory.

### Emacs - CIDER
[CIDER](https://cider.mx/) is a clojure(script) development environment for [Emacs](https://www.gnu.org/software/emacs/). To install CIDER, first add the [MELPA](https://melpa.org/#/getting-started) package archive to your Emacs installation. Then install CIDER as follows:

```
M-x package-install RET cider RET
```

Once CIDER has been installed, open the [src/covid_qc/core.cljs](src/covid_qc/core.cljs) file in Emacs. Then, start a CIDER REPL by running:

```
M-x cider-jack-in-cljs
```

![cider-jack-in-cljs](doc/images/cider-jack-in-cljs.png)

A new CIDER REPL buffer will be created, resembling this:

![cider-figwheel-startup](doc/images/cider-figwheel-startup.png)

At the same time, a local dev instance of the site will be served from http://localhost:9500.

From the `core.cljs` buffer, type `C-c M-n M-n` to switch the current REPL namespace to `covid-qc.core`. The REPL prompt should update to show the current namespace:

![repl-ns-covid-qc-core](doc/images/repl-ns-covid-qc-core.png)

You should now have a live REPL connection to the site. Open your browser's console:

![chrome-console-01](doc/images/chrome-console-01.png)

Now, in your REPL, type `(js/console.log "Hello, world")` and press enter:

![repl-hello-world](doc/images/repl-hello-world.png)

The expression is automatically compiled to JavaScript and evaluated in the browser. The message should appear in your browser console:

![console-hello-world](doc/images/console-hello-world.png)

### VS Code - Calva
[Calva](https://calva.io/) is a clojure(script) development environment for [VS Code](https://code.visualstudio.com/). Search for `calva` in the VS Code Extensions marketplace to install it.

Open the `covid-qc` folder in VS Code. Open the VS Code Command Palette (`Ctrl-Shift-P`), and search for `calva`:

![vscode-command-palette-calva-start](doc/images/vscode-command-palette-calva-start.png)

Select "Calva: Start a Project REPL and Connect".

When prompted for a project type, select 'deps.edn + Figwheel Main':

![vscode-command-palette-calva-select-project-type](doc/images/vscode-command-palette-calva-select-project-type.png)

When prompted to select aliases, make sure that no aliases are selected, and click 'OK':

![vscode-command-palette-calva-select-aliases](doc/images/vscode-command-palette-calva-select-aliases.png)

When prompted to select builds, select 'dev' and click 'OK':

![vscode-command-palette-calva-select-builds](doc/images/vscode-command-palette-calva-select-builds.png)

A calva evaluation results output window should appear:

![vscode-calva-output-window](doc/images/vscode-calva-output-window.png)

...and there should be messages printed to the terminal showing that the site has been compiled and served at http://localhost:9500

![vscode-calva-terminal](doc/images/vscode-calva-terminal.png)

