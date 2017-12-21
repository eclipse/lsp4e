Issues and code contribution are handled via Eclipse.org infrastructure. See https://projects.eclipse.org/projects/technology.lsp4e/developer

_TL;DR_: `mvn verify` should
build a p2-accessible repository in `repository/target`.

# Development

This project is built using _Maven Tycho_, a set of extensions to
Maven for building Eclipse bundles and features.

## Requirements

1. Maven 3.1.1 or later. Although m2eclipse is bundled with its own Maven install,
   Maven is necessary for command-line builds.

1. JDK 8

1. git (optional: you can use EGit from within Eclipse instead)

1. The [Eclipse IDE](https://www.eclipse.org/downloads/eclipse-packages/).
  It's easiest to use the _Eclipse IDE for Java EE Developers_ package. You can use
  Eclipse 4.7 (Oxygen).

  1. The [m2eclipse plugin](http://www.eclipse.org/m2e/) (also called m2e) is
     required to import the projects into Eclipse. m2eclipse is included in
     [several packages](https://www.eclipse.org/downloads/compare.php?release=neon),
     such as the _Eclipse IDE for Java EE Developers_ package.

1. Clone the project to a local directory using `git clone
   git clone ssh://username@git.eclipse.org:29418/lsp4e/lsp4e.git`.

## Configuring Maven/Tycho Builds

The plugin is built using Maven/Tycho and targeted to Java 8.


### Changing the Eclipse Platform compilation and testing target

By default, the build is targeted against Eclipse Oxygen / 4.7.
You can explicitly set the `target-config` property to
`target-platform-oxygen` (4.6) or `target-platform-photon` (4.8).

```
$ mvn -Dtarget-config=target-platform-photon verify
```

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

6. In the Project Explorer, select the file target-platform-photon.target. Open this
   file with the target editor.

7. Click the "Set as Target Platform" link on the top right.

## Contributing Changes

When sending a pull request please squash your commits and set the commit message
in this form:

Bug 528908 - Change Description
Signed-Off-By: Your Name <email@example.org>
Change-Id: I8cd4aa13a8c61b550cfc80f68ab2b4d230b9f9b3

To push to Gerrit for review:

```
$ git push ssh://username@git.eclipse.org:29418/lsp4e/lsp4e.git HEAD:refs/for/master
```

Make sure that the name/email in the git commits matches the name/email you have
registered with the Eclipse project. 
