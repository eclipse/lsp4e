## Troubleshooting language servers with LSP4E

### Log and analyze LSP messages

In the vast majority of cases, when troubleshooting a Language Server integration, the unique source of truth are the LSP messages that are exchanged between the IDE (Eclipse IDE with LSP4E here) and the language server.

LSP4E allows to easily get those messages, in both directions, logged in a file. To get it, go to _Preferences > Language Servers > Logs_ and find the interesting Language Server to inspect. Then double click on the `Log to file` column to enable logging. A popup will suggest that you restart the IDE, but what you can simply close all files associated to the target Language Server and reopen them, what matters isn't that the IDE restarts, but more that the Language Server restarts.

Then in the _<workspace>/languageServers-log_ folder, you'll see files containing the raw messages and a timestamp. You can then inspect for typical cause of failures such as:
* an expected message was not sent or received
* a message contains an erroneous value
* a message doesn't receive a response in due time
* ...

### Debug the language server

TODO