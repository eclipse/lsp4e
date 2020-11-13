## LSP4E Changelog and New and Noteworthy

### v0.17.0 (Nov 2020)

* Target Platform updated to:
    * SimRel 2020-09, including Eclipse Platform 4.17
    * Eclipse LSP4J 0.10.0
* Java 11 is required to build LSP4E and run tests
* Java 11 is required to run some of the bundles. For now just the org.eclipse.lsp4e.jdt and test bundles. In a future release it may encompass all the bundles. The Eclipse Platform now has bundles that require Java 11 and some of those bundles may be dependencies of LSP4E.
* Highlight.js is now being used for lsp4e's completion documentation. See [Bug 565496](https://bugs.eclipse.org/bugs/show_bug.cgi?id=565496)
* LSP4E's format menu item has been relocated Generic Editor Source submenu
* New icon for unit completion kind. See [Bug 567812 ](https://bugs.eclipse.org/bugs/show_bug.cgi?id=567812)
* [CommandExecutor](org.eclipse.lsp4e/src/org/eclipse/lsp4e/command/CommandExecutor.java) has been updated with the improvements that were originally developed in WildWebDeveloper. Migrating those changes back to LSP4E makes these improvements available for all consumers of LSP4E.

See also the [Bugzilla issues](https://bugs.eclipse.org/bugs/buglist.cgi?product=lsp4e&target_milestone=0.17.0) for details of bug fixes and other items changed in this release.

Breaking Changes:

* Eclipse LSP4J has some breaking API changes. While LSP4E's directly declared API did not change,
  places where LSP4J types are exposed to LSP4E's client code there may be some API change effects.
  Please consult [LSP4J v0.10.0](https://github.com/eclipse/lsp4j/releases/tag/v0.10.0) for more
  details.

---

### Older releases

In Release 0.17.0 The Changelog was migrated to live in the LSP4E git repo. For older
versions, please see the [release records on the PMI](https://projects.eclipse.org/projects/technology.lsp4e)