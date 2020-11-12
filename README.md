# Eclipse LSP4E

[Eclipse LSP4E](https://projects.eclipse.org/projects/technology.lsp4e) makes Eclipse IDE able to consume the [Language Server protocol (LSP)](https://microsoft.github.io/language-server-protocol/) and the [Debug Adapter protocol (DAP)](https://microsoft.github.io/debug-adapter-protocol/).

**Target audience** are Eclipse plugin developers or Language developers willing to integrate a language which ships a Language Server or Debug Adapter into Eclipse IDE. End-users can also take advantage of this as LSP4E also defines a way to bind Eclipse IDE to existing language servers from UI.

**Install it into Eclipse IDE, or add it to your target-platform** using one of this p2 repository:
* http://download.eclipse.org/lsp4e/releases/latest/ for latest release
* http://download.eclipse.org/lsp4e/snapshots/ for latest CI build (usually of decent quality)

## Preview

### Language Server in Eclipse in the Eclipse IDE
[<img alt="Video demo" src="https://content.screencast.com/users/mistria/folders/Default/media/1a860eda-8a50-4668-874c-ee2dd2ef213c/FirstFrame.jpg" width="400px">](http://www.screencast.com/t/Xs3TtaQM)

### Debug Adapter in action in the Eclipse IDE
[<img alt="Video demo" src="https://content.screencast.com/users/mistria/folders/Default/media/8112c854-eaae-4fd4-b863-84a39e848647/FirstFrame.jpg" width="400px">](https://www.screencast.com/t/0QRpxSA3M7Qy)

## Features

### Edition
LSP4E mostly ships extensions to the [Generic Editor proposal for Eclipse Platform Text](https://www.eclipse.org/eclipse/news/4.7/M3/#generic-editor). But those classes can be reused in any editor or other extensions.

At the moment, it provides regular JFace/Platform Text classes for:
* detection of language server for given file
* synchronization of files with Language Server
* diagnostics as problem markers
* completion
* hover
* jump to declaration
* formatting
* rename refactoring
* Find References
* File symbols (as Outline or Quick Outline)
* Workspace symbols
* Language Server messages as notifications

### Debug

Support for the Debug Adapter Protocol includes usual debug operations (breakpoints, step forward, step into, view variable value, evaluate expression, change variable value...) in the Platform Debug framework and its related UI components.

## Examples

Examples of integration contain:
* [Eclipse Corrosion](https://github.com/eclipse/corrosion) **Rust** support plugin for Eclipse IDE, edition is powered by LSP4E and RLS.
* [Eclipse aCute](https://github.com/eclipse/aCute) **C#** edition in Eclipse IDE using LSP4E and OmniSharp LSP implementation.
* End-user using Language Server from **Docker image**, providing edition feature in the IDE without creating specific Eclipse IDE plugin: http://www.screencast.com/t/vksX3uZm1aj
* [Eclipse WildWebDeveloper](https://github.com/eclipse/wildwebdeveloper) **HTML, CSS, JavaScript, TypeScript, Node.js, Angular, JSON, YAML (+Kubernetes) and XMLL** edition, using LSP4E and various language servers from SourceGraph and VSCode.
* [language-servers-for-eclipse](https://github.com/eclipselabs/language-servers-for-eclipse) Binds various language servers to the Eclipse IDE.
* [Solargraph](https://github.com/PyvesB/eclipse-solargraph) Binds a **Ruby** language server to the Eclipse IDE.

All those examples are good for usage as they provide advanced edition features, and great for showcase of the LSP4E project.

## Community

Contributions are highly welcome. [See how](CONTRIBUTING.md)

## Related projects

The [Language Server protocol](https://microsoft.github.io/language-server-protocol/) specification is an open-source project.

As the Language Server Protocol doesn't include support for syntax highlighting, most adopters of LSP4E usually pair it with the [Eclipse TM4E](https://projects.eclipse.org/projects/technology.tm4e) project to provide Syntax Highlighting according to TextMate grammars.

Possible integration with Docker images as language-server are made possible thanks to **Eclipse Docker Tools**, which are part of the [Eclipse LinuxTools](https://projects.eclipse.org/projects/tools.linuxtools) project.

## History

This has been initiated during the EclipseCon France 2016 Unconference with the first official release
in [February 2017](https://projects.eclipse.org/projects/technology.lsp4e/releases/0.1.0).
