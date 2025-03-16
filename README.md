# Gen

<img src="logo.svg" alt="Gen logo" style="height: 50px;">

A CLI tool to generate files and run commands from templates.

It is intended to make it easy to create new custom projects and generate files within existing projects.
Like `rails new` and `rails generate`, but generic.

``` sh
$ gen from/here to/there --name foo
write to/there/deps.edn
write to/there/src/foo.clj
run   git init
```

_Special thanks to [jaide](https://github.com/jaidetree/) for the logo!_

## Install

You need [babashka](https://github.com/babashka/babashka?tab=readme-ov-file#installation) and [bbin](https://github.com/babashka/bbin?tab=readme-ov-file#installation) to install `gen`.

Then run `bbin install https://github.com/filipesilva/gen.git`.
You should now be able to run `gen --version` and `gen --help`.


## Usage

`gen` parses variables like `{{name}}` in file paths, file contents, and commands, then replaces them with the values you provided in CLI flags like `--name foo`.

To get started make a new template folder, and put a file with a template var there:

``` sh
mkdir my-first-template
echo "Hello {{x}}!" > my-first-template/hello.md
```

Now you can use that template folder to generate new files:

``` sh
$ gen my-first-template my-first-gen --x world
write my-first-gen/hello.md

$ cat my-first-gen/hello.md
Hello world!
```

You can also use git repositories such as sources:

``` sh
$ gen https://github.com/filipesilva/gen-license ./ --author "John Doe"
write LICENSE

$ cat LICENSE
MIT License

Copyright (c) 2025 John Doe

Permission is hereby granted, free of charge, to any person obtaining a copy
...
```

In this example both "2025" and "John Doe" were templated, but we only provided a value for `author`.
That's because `gen` comes with a few default vars that you can see in `gen --help`:

``` sh
$ gen --help
...
Default vars:
  --dest-name            Defaults to last part of `dest`
  --date      2025-01-11 Current date as yyyy-mm-dd
  --year      2025       Current year
...
```

## Filters

Parsing uses [Selmer](https://github.com/yogthos/Selmer).
Check the Selmer docs for advanced variable usage like `{{name|capitalize}}` filters and `{% if condition %}yes!{% endif %}` conditionals.

`gen` adds a few filters of it's own related to casing, courtesy of [camel-snake-kebab](https://clj-commons.org/camel-snake-kebab/):
- pascal-case
- camel-case
- screaming-snake-case
- snake-case
- kebab-case
- camel-snake-case
- http-header-case

Snake case is especially useful for Clojure filenames.
Use `src/{{my-var|snake-case}}.clj` to turn a `foo-bar` var into `foo_bar`.


## Configuration

`gen` will look for configuration in `gen.edn` on the `source` directory.
You can use these files to control `gen` and use extra functionality.

You can see help for the config format using `gen --config-help.`
The defaults are:

``` edn
{:root ""           ;; root for template files
 :vars {}           ;; default vars, e.g. {:author "me"}
 :cmds []           ;; run in shell after file generation, e.g. ["git init"]
 :default-alias nil ;; used if no alias is specified
 :aliases {}}       ;; aliases are extra sets of configuration that are merged on top
```

[EDN](https://learnxinyminutes.com/edn/) is like JSON but better, and the norm in Clojure projects.

The `:cmd` key is especially useful to initialize git repositories and install project dependencies.
For instance, https://github.com/filipesilva/gen-scratch has this `gen.edn`:

``` edn
{:cmds ["git init"]}
```

Calling `gen` with this template will show the files created and the ran commands:

``` sh
$ gen https://github.com/filipesilva/gen-scratch foo
write foo/README.md
write foo/deps.edn
write foo/src/foo.clj
run   git init
```

Aliases let you have multiple configuration sets in a single configuration file.
The https://github.com/filipesilva/gen-license template we used before has this `gen.edn`:

``` edn
{:default-alias :mit
 :aliases
 {:mit       {:root "mit"}
  :eclipse   {:root "eclipse"}
  :unlicense {:root "unlicense"}}}
```

When we called `gen https://github.com/filipesilva/gen-license . --author "John Doe"` we didn't specify any alias.
But since there was a `:default-alias` set to `:mit` we used the `root` folder in the `:mit` alias.

To use the `:eclipse` alias, add it at the end of source `gen https://github.com/filipesilva/gen-license:eclipse`.
We don't need to provide a value for `author` because the eclipse license template does not use that var.

Multiple aliases stack on top of each other, and the resulting merged config will be used.

`gen` will also look for a `~/.gen-global.edn` file where you can set names for sources and global default vars:

``` edn
{;; shorthands e.g. `gen scratch app`
 :sources {:scratch "~/repos/gen-scratch"
           :license "https://github.com/user/license:mit"}
 ;; global defaults, local and cli vars will overwrite these
 :vars    {:author "John Doe"
           :email  "john@doe.com"}}

```

Now calling `gen license .` is the same as `gen https://github.com/user/license:mit . --author "John Doe"`.


## Usage in a existing project

Besides making new projects, `gen` can be used in a project you already have and where you want to make new files from project-specific templates, like `rails generate`.

Imagine you want to make unit tests with a default format.

Start by adding a `gen.edn` at the project root, and the template files:

``` edn
{:default-alias :test
 :aliases
 {:test {:root "templates/test"}}}
```

``` clojure
;; templates/test/test/{{name|snake-case}}_test_.clj
(ns {{name}}-test
  (:require [clojure.test :refer [deftest is testing]]))

(deftest missing
  (is false))
```

Now run `gen test --name foo`:

``` sh
$ gen test --name foo
write test/foo_test.clj
```

Notice how you didn't have to specify the source.
`test` is the destination directory, and the default alias is `:test`.
When you omit the source, `gen` will look up from the current dir for the first `gen.edn` that it finds and use that as the source.

You can use multiple aliases and use them on the source argument like `gen :test test --name foo`.
This way you can make your own template library for your project.


## xforms

Gen can also transform (xform) files using dynamically loaded transformer functions registered to file extensions in configuration.

Let's look at an example. Given this source configuration at `xforms/gen.edn`:

``` edn
{:deps   {} ;; no deps needed since these xforms are built-in
 :xforms {".append"  filipesilva.gen.xforms/append
          ".prepend" filipesilva.gen.xforms/prepend}}
```

And the following template files with newlines at the end:
- `xforms/text.md`: `original`
- `xforms/text.md.prepend`: `before`
- `xforms/text.md.append`: `after`

``` sh
$ gen xforms ./
write text.md
xform text.md
xform text.md
```

`xforms/text.md.prepend` prepended its content to `xforms/text.md`, and `xforms/text.md.append` appended its.
`text.md`'s final content is:

```
before
original
after
```

You can declare your own xforms and import them using the configuration `:deps`.
The implementation for the transforms referenced above is:

``` clojure
(ns filipesilva.gen.xforms)

(defn append [file xform]
  (str file xform))

(defn prepend [file xform]
  (str xform file))
```


## Clojure library

You can use `gen` in your clojure projects to generate files.

``` clojure
(require '[filipesilva.gen :as gen])

(gen/generate {:source "from/here"
               :dest   "to/there"
               :root   "." ;; root within :source
               :vars   {:name "foo"}
               :cmds   ["git init"]
               :allow-missing false
               :overwrite false
               :dry-run false})
```



## Development

You can install the local repo using `bbin install .`

`bb test` and `bb e2e` for testing.

Version is auto determined from the git repository
