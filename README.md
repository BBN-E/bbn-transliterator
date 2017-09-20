Code to transliterate human language from one character system to another.  This software draws
heavily on 'uroman' written by Ulf Hermjakob, USC Information Sciences Institute (2015-2016).
This partial port to Java was made by Ryan Gabbard in 2017 for better integration into BBN's
Java-based DARPA LORELEI system.  All the hard work (and any citations) should be credited to him
and all the errors to me.

The current implementation was made very quickly with the goal of matching uroman output on certain
languages as quickly as possible.  Runtime performance was not a goal, so there are many
low-hanging fruit for optimization. Additionally, the scoring scheme used for alternate analyses is
currently a hack and needs rethinking.
