Issues and code contribution are handled via Eclipse.org infrastructure. See https://projects.eclipse.org/projects/technology.lsp4e/developer

_TL;DR_: `mvn verify` will build a p2-accessible repository in `repository/target/repository`.

# Development

This project is built using _Maven Tycho_, a set of extensions to
Maven for building Eclipse bundles and features.

## Requirements

1. Maven 3.6.3 or later. Although m2eclipse is bundled with its own Maven install,
   Maven is necessary for command-line builds.

1. JDK 11. Only Java 8 is required to run core LSP4E, but some components, such as JDT extensions and tests Java 11 is required. Some dependencies of LSP4E may also require Java 11.

1. git (optional: you can use EGit from within Eclipse instead)

1. The [Eclipse IDE](https://www.eclipse.org/downloads/eclipse-packages/).
  It's easiest to use the _Eclipse IDE for Enterprise Java Developers_ package.

  1. The [m2eclipse plugin](http://www.eclipse.org/m2e/) (also called m2e) is
     required to import the projects into Eclipse. m2eclipse is included in
     [several packages](https://www.eclipse.org/downloads/packages/compare),
     such as the _Eclipse IDE for Enterprise Java Developers_ package.

1. Clone the project to a local directory using `git clone ssh://username@git.eclipse.org:29418/lsp4e/lsp4e.git`.

## Configuring Maven/Tycho Builds

The plugin is built using Maven/Tycho and targeted to Java 8.


### Eclipse Platform Target Platform

The build is generally targeted against the newest release of the Eclipse
Platform. Refer to [target-platform-latest.target](target-platforms/target-platform-latest/target-platform-latest.target) for details on the current dependencies used
to build LSP4E.

## Import into Eclipse

Prerequisites:

1. You have installed _Eclipse for RCP and RAP Developers_ 4.7 or later. 
   Other distributions that include PDE and reasonable Java support may also work.

2. You have cloned the lsp4e repo into a local directory.

In Eclipse:

1. **File > Import > Existing Maven Projects**

2. Click **Next**. 

3. In the **Root Directory** chooser select the directory where you cloned the project,
   and which contains pom.xml.

4. All targets should be selected. Click **Finish**.

5. At this point the Problems view should show on the order of 1000 errors.

6. In the Project Explorer, select the file
   [target-platform-latest.target](target-platforms/target-platform-latest/target-platform-latest.target).
   Open this file with the target editor.

7. Click the "Set as Target Platform" link on the top right.

## Contributing Changes

When sending a pull request please squash your commits and set the commit message
in this form:

```
Bug 528908 - Change Description

Change details, if needed

Signed-Off-By: Your Name <email@example.org>
Change-Id: I8cd4aa13a8c61b550cfc80f68ab2b4d230b9f9b3
```
To push to Gerrit for review:

```
$ git push ssh://username@git.eclipse.org:29418/lsp4e/lsp4e.git HEAD:refs/for/master
```

Please see [Gerrit info on the Eclipse Wiki](https://wiki.eclipse.org/Gerrit) for more information on how to use Gerrit.

Make sure that the name/email in the git commits matches the name/email you have
registered with the Eclipse project, that you have signed the ECA and that you have
signed-off your commit. See the section Eclipse Contributor Agreement below for more
information.

## Bug Tracking

This project uses Bugzilla to track ongoing development and issues.

* Search for issues: https://bugs.eclipse.org/bugs/buglist.cgi?product=LSP4E
* Create a new report:
   https://bugs.eclipse.org/bugs/enter_bug.cgi?product=LSP4E

Be sure to search for existing bugs before you create another one. Remember that
contributions are always welcome!

## Eclipse Development Process

This Eclipse Foundation open project is governed by the Eclipse Foundation
Development Process and operates under the terms of the Eclipse IP Policy.

## Eclipse Contributor Agreement

Before your contribution can be accepted by the project team contributors must
electronically sign the Eclipse Contributor Agreement (ECA).

* http://www.eclipse.org/legal/ECA.php

Commits that are provided by non-committers must have a Signed-off-by field in
the footer indicating that the author is aware of the terms by which the
contribution has been provided to the project. The non-committer must
additionally have an Eclipse Foundation account and must have a signed Eclipse
Contributor Agreement (ECA) on file.

For more information, please see the Eclipse Committer Handbook:
https://www.eclipse.org/projects/handbook/#resources-commit

## Contact

Contact the project developers via the project's "dev" list.

* https://dev.eclipse.org/mailman/listinfo/lsp4e-dev
