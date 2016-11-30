This repository contains experiments to make Eclipse IDE able to consume the [Language Server protocol](https://github.com/Microsoft/language-server-protocol).

**Install it into Eclipse IDE** using this p2 repository:  http://repository.jboss.org/nexus/content/unzip/unzip/org/eclipse/languageserver/repository/0.1.0-SNAPSHOT/repository-0.1.0-SNAPSHOT.zip-unzip/


[<img alt="Video demo" src="http://content.screencast.com/users/mistria/folders/Default/media/1a860eda-8a50-4668-874c-ee2dd2ef213c/FirstFrame.jpg" width="400px">](http://www.screencast.com/t/Xs3TtaQM)

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

Extensions to the [Generic Editor proposal for Eclipse Platform Text](https://bugs.eclipse.org/bugs/show_bug.cgi?id=497871) are provided so having the generic editor + this bundle enables the LSP based behavior in the Generic editor. But those classes can be reused in any editor or other extensions. Examples of integration contain:
* C# edition in Eclipse IDE using OmniSharp LSP implementation
* JSON (with schema) using VSCode LSP impl
* CSS using VSCode LSP impl.

See also [Documentation Index](/adoc/index.adoc).

This has been initiated during the EclipseCon France 2016 Unconference. Contributions are highly welcome using GitHub issues and PR at the moment.

This piece of work is meant to move to some Eclipse.org project then inside the Eclipse IDE package directly as soon as it is considered stable and isable enough.
