## Using a language server with Eclipse IDE, without code

Eclipse LSP4E provides a way to dynamically define a language server and associate it with some files in the Eclipse IDE, without coding the integration.

This is quite handy for testing a language server, however, it's not recommended to rely on it for actual production usage (you should prefer using the `org.eclipse.lsp4e.languageServers` extension point for that).

Requirements for the language server
* The Language Server must be "runnable" from command-line
* The Language Server must start a specific process
* The Language Server must support communication via __stdin & stdout__ (other streams not supported for this case)

### Step 1: Identify or define the content-type for your Language Server targets

In Preferences > Content-Type, check that either a content-type is already defined for your target files, and that this content type maps such files (by name or extension). If no content-type does that, you can augment an existing content-type or create a new content-type to associate with your target file name patterns/extensions.

📝 The content-type must be a descendant of the _Text_ content-type.

### Step 2: Create a Launch Configuration describing how to start your Language Server

In the usual _Launch Configurations_ dialog, define how your language server should be started. Depending on your language server, you may want to start it as a Java Program or as an External Program... Use the various fields to customize things like command-line arguments or environment variables if necessary.

To help you with development and testing, it's usually nice to be able to monitor the language server output. So you may want to tick the _Command > Allocate Console_ checkbox.

### Step 3: Associate content-type with Language Server launch configuration

_Preferences > Language Servers_, on the bottom block _Add..._. Then select
* on the left: your target content-type
* on the right: the Launch Configuration that can start your language server

Another option on the association dialog allows to select the "launch mode". For configurations that allows it, you can for example use _debug_ instead of _run_ and that will allow you to benefit from debug capabilities.

Then apply.

### Step 4: See the result in action and monitor the Language Server

Open one of your target files with the Generic Editor (_Right-click > Open With > Generic Editor_), if the file is already open in some editor, you'll need to close and reopen it. Once open in the Generic Editor, you should enjoy rich edition: diagnostics/problems, hover, completion, outline... coming from the Language Server.

If you've enabled the _Allocate Console_ option, the console should show the messages sent by the Language Server to LSP4E.

In the _Debug_ view, you can monitor the state of your Language Server process. Depending on the Launch Configuration type and other options, you have more or less monitoring and control capabilities available.

### Bonus: Developing, testing and debugging a Language Server in the same Eclipse IDE

During [Step 3](#Step 3: Associate content-type with Language Server launch configuration), if you used the _debug_ mode, then the _Debug_ view should show you more details about your language server running. Typically, if your language server is developed with Java or Node and you used related launch configurations, you'll see in debug the various frames/stacks/threads... that you can pause and inspect. Breakpoints would work too.

So LSP4E is also a good way to develop and debug your language-server in the same IDE instance with this approach: you can have the sources of your language server imported, create a Launch Configuration for your Language Server from the project, place breakpoints and so on, associate this launch configuration with the target content-type and start editing the target files to see the breakpoints hit in your IDE.