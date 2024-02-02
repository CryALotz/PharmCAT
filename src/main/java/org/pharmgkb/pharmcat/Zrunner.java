

 ////copy paste this scrip in the same directory as PhamCat Class

 package org.pharmgkb.pharmcat;

 import java.io.File;
 import java.io.FilenameFilter;
 import java.util.stream.Collectors;
 import java.util.stream.IntStream;
 
 public class Zrunner
 {
   public static void massiveRuner(String[] args) {
 
     String inpDirectory = "/home/xixe/pharm/pharmvip-guideline/resources/samples/bigchunk";
    //  directory = "/nbt_main/home/kwankom/ws/vapp/called_tester/vcf_fix_row/" ;
    inpDirectory = args[0];

     File dir = new File(inpDirectory);
 
     File[] matches = dir.listFiles(new FilenameFilter()
     {
       public boolean accept(File dir, String name)
       {
 //        return name.startsWith("temp") && name.endsWith(".vcf");
         return name.endsWith(".vcf");
       }
     });
 
    //  for (int i = 0; i < matches.length; i++) {
    //    System.out.println(matches[i]);
    //  }

      String outTarget = args[1] ;

     for (int i = 0; i < matches.length; i++) {
        String sampleFilePathStr = matches[i].toString();
        String [] filePathSplited = sampleFilePathStr.split("/");
        String fileName = filePathSplited[filePathSplited.length -1 ];
        
        String outPath = outTarget + fileName + "_opt";

        System.out.println("processsing : " + sampleFilePathStr);
        String[] arguments = new String[] {"-vcf",sampleFilePathStr,
            "-o",outPath };

        // System.out.println(arguments);
        PharmCAT.main(arguments);
     }
 
 
   }
 
   public static void singleRuner() {
     String sample = "" ;
     sample = "00045394.vcf";
      
     String targetDir = "/nbt_main/home/kwankom/ws/vapp/called_tester/vcf_data/";
     targetDir = "/nbt_main/home/kwankom/ws/vapp/called_tester/vcf_fix_row/" ;
     String file = targetDir + sample;
     
     String outt = "";
     outt = "/nbt_main/home/kwankom/ws/pcat/PharmCAT/inpp/" + sample + "_opt";
     String[] arguments = new String[] {"-vcf",file,
         "-o",outt,};
     PharmCAT.main(arguments);
   }
   
   public static void main(String[] args) {
    // println(args);
    System.out.println(args);
     String s1 = ">";
     String s2 = "<";
     int n = 20;
     String sRepeated1 = IntStream.range(0, n).mapToObj(i -> s1).collect(Collectors.joining(""));
     String sRepeated2 = IntStream.range(0, n).mapToObj(i -> s2).collect(Collectors.joining(""));
 
     System.out.println(sRepeated1);
     System.out.println("Runerz Debuger Starting");
     System.out.println(sRepeated2);
    //  singleRuner();
    massiveRuner(args);
   }
 }
 