Code to transliterate human language from one character system to another.  This software draws
heavily on 'uroman' written by Ulf Hermjakob, USC Information Sciences Institute (2015-2016).
This partial port to Java was made by Ryan Gabbard in 2017 for better integration into BBN's
Java-based DARPA LORELEI system.  All the hard work should be credited to him
and all the errors to me.  Please note the citation requirement for uroman in LICENSE-uroman.txt.

The current implementation was made very quickly with the goal of matching uroman output on certain
languages as quickly as possible.  Runtime performance was not a goal, so there are many
low-hanging fruit for optimization. Additionally, the scoring scheme used for alternate analyses is
currently a hack and needs rethinking.

#### Legal notes

This work was funded by DARPA Contract HR0011-15-C-0013.

The views, opinions, and/or findings expressed are those of the author(s) and should not
be interpreted as representing the official views of policies of the Department of
Defense or the U.S. Government.  

Released as DARPA DISTRIBUTION A.  Approved for public release: distribution unlimited.