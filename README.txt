0. Create, in <code_path>, the folder for the code 
- e.g., C:/Users/Reinier/Desktop/code

1. Clone the following repositories into the code folder  
- https://github.com/reinierdevalk/tabmapper/  
- https://github.com/reinierdevalk/utils/ 
- https://github.com/reinierdevalk/formats-representations/ 

2. Go into the tabmapper/ folder; you won't need to interfere with the 
   contents of the other two folders.
  $ cd <code_path>/tabmapper
  
3. Open cp.sh, a script that constructs the classpath needed for the Java code to run.
- replace the value of the CODE_PATH variable with your full <code_path>

4. Make sure that tabmapper, the script to run, is accessible from anywhere on your
   computer (and not only from <code_path>/tabmapper)
- copy the script to usr/local/bin/ (or any directory that is already on the PATH) 
  $ cp tabmapper /usr/local/bin/
- ensure that the script has execute permissions
  $ chmod +x /usr/local/bin/tabmapper
- the directories in the PATH can be shown with  
  $ echo $PATH

5. Run TabMapper
  $ bash tabmapper.sh

5. If you want to run TabMapper from anywhere on your computer


5. When running TabMapper for the first time, the folder tabmapper/data/ and 
   its subfolders are created. Any of the folders in this folder structure are 
   recreated whenever they have been deleted and TabMapper is run again. 
   tabmapper/data/in/tab/	holds the tablature input files
   tabmapper/data/in/MIDI/	holds the MIDI input files (vocal models)
   tabmapper/data/out/		holds the output files


boolean

path to code = 	code_path/				F:/research/software/code/eclipse/
contains	code_path/tabmapper
		code_path/utils
		code_path/format-representations

path to data = 	data_path/
contains	data_path/in/tab/
		data_path/in/MIDI/
		data_path/out/ 




java -cp '.;../../utils/bin;../../utils/lib/*;../../formats-representations/bin;../../formats-representations/lib/*' tabmapper.TabMapper

does the same as

java -cp $(for p in ../../* ; do echo -n $p"/bin"";"$p"/lib/*"";" ; done) tabmapper.TabMapper

this works from any folder

java -cp $(for p in F:/research/software/code/eclipse/* ; do echo -n $p"/bin"";"$p"/lib/*"";" ; done) tabmapper.TabMapper

i.e., 

java -cp $(for p in <code_path>* ; do echo -n $p"/bin"";"$p"/lib/*"";" ; done) tabmapper.TabMapper

java -cp '.;F:/research/software/code/eclipse/utils/bin;F:/research/software/code/eclipse/utils/lib/*;F:/research/software/code/eclipse/formats-representations/bin;F:/research/software/code/eclipse/lib/*;F:/research/software/code/eclipse/tabmapper/bin' tabmapper.TabMapper



============

java -cp $(for p in F:/research/software/code/eclipse/* ; do echo -n $p"/bin"";"$p"/lib/*"";" ; done) ui.UI N-bwd-thesis-int-4vv . '' '' user/in/4465_33-34_memor_esto-2.tbp '-k=-2|-m=0'
java -cp $(for p in ../../* ; do echo -n $p"/bin"";"$p"/lib/*"";" ; done) ui.UI N-bwd-thesis-int-4vv . '' '' user/in/4465_33-34_memor_esto-2.tbp '-k=-2|-m=0'