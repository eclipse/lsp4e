## Properly integrating a language server with LSP4E

### General principle

Use the `org.eclipse.lsp4e.languageServer` extension in `plugin.xml` to define a language server (ie how to start it and connect to it) and to associate it with one or many content-types.

### Examples

See [multiple examples in the test](../org.eclipse.lsp4e.test/plugin.xml).

### Tips and Tricks

* Use the `ProcessStreamConnectionProvider` if your LS is accessible as a process.