# Contributing to Eclipse LSP4E

Welcome to the Eclipse LSP4E contributor land, and thanks in advance for your help in making Eclipse LSP4E better and better!

🏠 Official Eclipse LSP4E Git repo is [https://github.com/eclipse/lsp4e](https://github.com/eclipse/lsp4e) . (All other repositories, mirrors and so on are legacy repositories that should be removed at some point, so please don't use them!)

## ⚖️ Legal and Eclipse Foundation terms

The project license is available at [LICENSE](LICENSE).

This Eclipse Foundation open project is governed by the Eclipse Foundation
Development Process and operates under the terms of the Eclipse IP Policy.

Before your contribution can be accepted by the project team, 
contributors must have an Eclipse Foundation account and 
must electronically sign the Eclipse Contributor Agreement (ECA).

* [http://www.eclipse.org/legal/ECA.php](http://www.eclipse.org/legal/ECA.php)

For more information, please see the Eclipse Committer Handbook:
[https://www.eclipse.org/projects/handbook/#resources-commit](https://www.eclipse.org/projects/handbook/#resources-commit).

## 💬 Get in touch with the community

Eclipse LSP4E use mainly 2 channels for strategical and technical discussions

* 🐞 View and report issues through uses GitHub Issues at https://github.com/eclipse/lsp4e/issues. _📜 Migration to GitHub tracker took place in November 2021, for older tickets, see https://bugs.eclipse.org/bugs/buglist.cgi?product=lsp4e 📜_
* 📧 Join the lsp4e-dev@eclipse.org mailing-list to get in touch with other contributors about project organization and planning, and browse archive at 📜 [https://accounts.eclipse.org/mailing-list/m2e-dev](https://accounts.eclipse.org/mailing-list/lsp4e-dev)

## 🆕 Trying latest builds

Latest builds, for testing, can usually be found at `https://download.eclipse.org/lsp4e/latest/` .

## 🧑‍💻 Developer resources

### Prerequisites

Eclipse IDE with Plugin Development Environment installed for development; and Maven 3.8.x or Eclipse m2e if you want to run Maven build

### ⌨️ Setting up the Development Environment manually

* Clone this repository <a href="https://mickaelistria.github.io/redirctToEclipseIDECloneCommand/redirect.html"><img src="https://mickaelistria.github.io/redirctToEclipseIDECloneCommand/cloneToEclipseBadge.png" alt="Clone to Eclipse IDE"/></a> for lsp4e.
* _File > Open Projects from Filesystem..._ , select the path to LSP4E Git repo and the relevant children projects you want to import
* Depending on the task you're planning to work on, multiple workflows are available to configure the [target-platform](https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/concepts/target.htm?cp=4_1_5)
    * In many cases, this simplest workflow will be sufficient: Install latest LSP$E snapshot in your target-platform (can be your current IDE), or
    * If you don't want to mix versions of LSP4E, open  __target-platforms/target-platform-latest/target-platform-latest.target__  and  _Set as Target-Platform_  from the editor, or
* Open the project modules you want to work on (right-click > Open project) and their dependencies
* Happy coding!


### 🏗️ Build

Just `mvn clean verify`.

### ⬆️ Version bump

LSP4E tries to use OSGi Semantic Version (to properly expose its API contracts and breakage) and Reproducible Version Qualifiers (to minimize the avoid producing multiple equivalent artifacts for identical source). This requires the developer to manually bump version from time to time. Some rules are that:

* Versions are bumped on a __per module grain__ (bump version of individual bundles/features one by one when necessary), __DON'T bump version of parent pom, nor of other modules you don't change__
* __Versions are bumped maximum once per release__ (don't bump versions that were already bumped since last release)
* __Don't bump versions of what you don't change__
* __Bump version of the bundles you're modifying only if it's their 1st change since last release__
* Version bump may need to be cascaded to features that *include* the artifact you just changed, and then to features that *include* such features and so on (unless the version of those features were already bumped since last release).

The delta for version bumps are:

* `+0.0.1` (next micro) for a bugfix, or an internal change that doesn't surface to APIs
* `+0.1.0` (next minor) for an API addition
* `+1.0.0` (next major) for an API breakage (needs to be discussed on the mailing-list first)
* If some "smaller" bump already took place, you can replace it with your "bigger one". Eg, if last release has org.eclipse.lsp4e 1.16.1; and someone already bumped version to 1.16.2 (for an internal change) and you're adding a new API, then you need to change version to 1.17.0

### ➕ Submit changes

LSP4E only accepts contributions via GitHub Pull Requests against [https://github.com/eclipse/lsp4e](https://github.com/eclipse/lsp4e) repository.
