# Eclipse LSP4E

[Eclipse LSP4E](https://projects.eclipse.org/projects/technology.lsp4e) makes Eclipse IDE able to consume the [Language Server protocol](https://github.com/Microsoft/language-server-protocol).

**Target audience** are Eclipse plugin developers or Language developers willing to integrate a language which ships a Language Server ino Eclipse IDE. End-users can also take advantage of this as LSP4E also define a way to bind Eclipse IDE to existing language servers from UI.

**Install it into Eclipse IDE, or add it to your target-platform** using one of this p2 repository:
* http://download.eclipse.org/lsp4e/releases/latest/ for latest release
* http://download.eclipse.org/lsp4e/snapshots/ for latest CI build (usually of decent quality)

## Preview

[<img alt="Video demo" src="http://content.screencast.com/users/mistria/folders/Default/media/1a860eda-8a50-4668-874c-ee2dd2ef213c/FirstFrame.jpg" width="400px">](http://www.screencast.com/t/Xs3TtaQM)

## Features

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

## Examples

Examples of integration contain:
* [aCute](https://github.com/mickaelistria/aCute) C# edition in Eclipse IDE using LSP4E and OmniSharp LSP implementation
* [BlueSky](https://github.com/mickaelistria/eclipse-bluesky) HTML (with embedded JS and CSS), CSS, JSON (with schema), JavaScript and TypeScript edition, using LSP4E and language servers.
* [lsp4e-php](https://github.com/eclipselabs/lsp4e-php) Binds a PHP language server to the Eclipse IDE.

All those examples are already good for usage as they provide advanced edition features, and great for showcase of the LSP4E project.

## Community

Contributions are highly welcome. [See how](https://projects.eclipse.org/projects/technology.lsp4e/developer}

## Related projects

As the Language Server Protocol doesn't include support for syntax highlighting, most adopters of LSP4E usually pair it with the [Eclipse TM4E](https://projects.eclipse.org/projects/technology.tm4e) project to provide Syntax Highlighting according to TextMate grammars.

## History

This has been initiated during the EclipseCon France 2016 Unconference. Some [initial documentation](/adoc/index.adoc) is still available (although it may not be up to date).
