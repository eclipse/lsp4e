# Eclipse LSP4E

[Eclipse LSP4E](https://projects.eclipse.org/projects/technology.lsp4e) makes Eclipse IDE able to consume the [Language Server protocol](https://github.com/Microsoft/language-server-protocol) and the [Debug Adapter protocol](https://github.com/Microsoft/vscode-debugadapter-node/tree/master/protocol).

**Target audience** are Eclipse plugin developers or Language developers willing to integrate a language which ships a Language Server or Debug Adapter into Eclipse IDE. End-users can also take advantage of this as LSP4E also define a way to bind Eclipse IDE to existing language servers from UI.

**Install it into Eclipse IDE, or add it to your target-platform** using one of this p2 repository:
* http://download.eclipse.org/lsp4e/releases/latest/ for latest release
* http://download.eclipse.org/lsp4e/snapshots/ for latest CI build (usually of decent quality)

## Preview

### Language Server in Eclipse in the Eclipse IDE
[<img alt="Video demo" src="http://content.screencast.com/users/mistria/folders/Default/media/1a860eda-8a50-4668-874c-ee2dd2ef213c/FirstFrame.jpg" width="400px">](http://www.screencast.com/t/Xs3TtaQM)

### Debug Adapter in action in the Eclipse IDE
[<img alt="Video demo" src="http://content.screencast.com/users/mistria/folders/Default/media/ab3a1d91-3f36-4ba1-85bb-657b22c3db3f/FirstFrame.jpg" width="400px">](http://http://www.screencast.com/t/vksX3uZm1aj)

## Features

### Edition
LSP4E mostly ships extensions to the [Generic Editor proposal for Eclipse Platform Text](https://www.eclipse.org/eclipse/news/4.7/M3/#generic-editor) are provided. But those classes can be reused in any editor or other extensions.

At the moment, it provides regular JFace/Platform Text classes for:
* detection of language server for given file
* synchronization of files with Language Server
* diagnostics as problem markers
* completion
* hover
* jump to declaration
* Find References
* File symbols (as Outline or Quick Outline)
* Workspace symbols
* Language Server messages as notifications

### Debug

Support for the Debug Adapter Protocol includes usual debug operations (breakpoints, step forward, step into, view variable value, evaluate expression, change variable value...) in the Platform Debug framework and its related UI components.

## Examples

Examples of integration contain:
* [Eclipse Corrosion](https://github.com/eclipse/corrosion) **Rust** support plugin for Eclipse IDE, edition is powered by LSP4E and RLS.
* [Eclipse aCute](https://github.com/eclipse/aCute) **C#** edition in Eclipse IDE using LSP4E and OmniSharp LSP implementation
* End-user using Language Server from **Docker image**, providing edition feature in the IDE without creating specific Eclipse IDE plugin: http://www.screencast.com/t/vksX3uZm1aj
* [BlueSky](https://github.com/mickaelistria/eclipse-bluesky) **HTML (with embedded JS and CSS), CSS, JSON (with schema), JavaScript and TypeScript** edition, using LSP4E and various language servers from SourceGraph and VSCode.
* [lsp4e-php](https://github.com/eclipselabs/lsp4e-php) Binds a **PHP** language server to the Eclipse IDE.
* [lsp4e-python](https://github.com/eclipselabs/lsp4e-python) Binds a **Python** language server to the Eclipse IDE.

All those examples are already good for usage as they provide advanced edition features, and great for showcase of the LSP4E project.

## Community

Contributions are highly welcome. [See how](CONTRIBUTING.md)

## Related projects

The [Language Server protocol](https://github.com/Microsoft/language-server-protocol) specification is an open-source project.

As the Language Server Protocol doesn't include support for syntax highlighting, most adopters of LSP4E usually pair it with the [Eclipse TM4E](https://projects.eclipse.org/projects/technology.tm4e) project to provide Syntax Highlighting according to TextMate grammars.

Possible integration with Docker images as language-server are made possible thanks to **Eclipse Docker Tools**, which are part of the [Eclipse LinuxTools](https://projects.eclipse.org/projects/tools.linuxtools) project.

[Eclipse LSPHub](https://projects.eclipse.org/projects/technology.lsphub) project is aimed at setting up a registry of language servers for automatic discovery and provisioning.

## History

This has been initiated during the EclipseCon France 2016 Unconference. Some [initial documentation](/adoc/index.adoc) is still available (although it may not be up to date).
