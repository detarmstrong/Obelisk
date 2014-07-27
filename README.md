# Obelisk

Obelisk is a companion to the Redmine project management application that runs on the Linux/Mac/Windows desktop. Use it to speed up the creation and organization of tickets using simple keyboard shortcuts:


## Usage

[Download the app!](https://github.com/detarmstrong/Obelisk/releases/latest)  

Once downloaded, from the terminal run `java -jar pathtothe.jar`  

A dialog box will appear upon opening the app that requires your Redmine API key. You can find your API key on your Redmine account page when logged in, on the right-hand pane.  

Start typing into the notepad. Select text and choose a shortcut action:

| Shortcut | Description |
|----------|------------|
| ctrl+t | Create tickets from selected lines. Lines starting with // will populate the description |  
| ctrl+r | Find the selected id and insert it's subject into the notepad |  
| ctrl+g | Search the selected text in redmine. Opens search results in browser |
| ctrl+f | Search the notepad for text. Does not operate on selected text in notepad |


## License

Copyright (C) 2012 Danny Armstrong

Distributed under the Eclipse Public License, the same as Clojure.
