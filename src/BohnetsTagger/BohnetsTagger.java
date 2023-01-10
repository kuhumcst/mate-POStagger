import java.io.*;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

import is2.data.Cluster;
import is2.data.DataF;
import is2.data.DataFES;
import is2.data.F2SF;
import is2.data.FV;
import is2.data.Instances;
import is2.data.Long2Int;
import is2.data.Long2IntInterface;
import is2.data.Parse;
import is2.data.PipeGen;
import is2.data.SentenceData09;
import is2.io.CONLLReader09;
import is2.io.CONLLWriter09;
import is2.tools.Retrainable;
import is2.tools.Tool;
import is2.util.DB;
import is2.util.OptionsSuper;
import is2.util.ParserEvaluator;

import is2.tag.Tagger;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.httpclient.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*বাংলা this file is UTF-8 encoded*/

@SuppressWarnings("serial")
public class BohnetsTagger extends HttpServlet 
    {
    // Static logger object.  
    private static final Logger logger = LoggerFactory.getLogger(BohnetsTagger.class);
    private static final String TMP_DIR_PATH = "/tmp";
    
    
    public String readProperty(String property)
        {
        String ret = null;
        try
            {
            FileInputStream fis = new java.io.FileInputStream(getServletContext().getRealPath("/WEB-INF/classes/properties.xml"));
            Properties prop = new Properties();
            try 
                {
                prop.loadFromXML(fis);
                ret = prop.getProperty(property,"");
                }
            catch (IOException io)
                {
                logger.warn("could not read properties. Message is " + io.getMessage()); 
                }
            finally
                {
                fis.close();
                }
            }
        catch (IOException io)
            {
            logger.warn("Could not read properties file. Message is " + io.getMessage()); 
            }	
        return ret;
        }

    public void init(ServletConfig config) throws ServletException 
        {
        super.init(config);
        }

    public String modelFileName(String language)
        {
        if(language.length() > 3)
            return language; // Language is in fact path to model
        String modelPathHead = readProperty("modelPathHead");
        String modelPathTail = readProperty("modelPathTail");
        String[] models = readProperty("models").split(",");

        for(int i = 0 ;i < models.length ; i++)
            {
            String lang = models[i];
            if(language.equals(lang))
                {
                return readProperty("modelPathHead")+lang+readProperty("modelPathTail")+readProperty(lang);
                }
            }
        return "";
        }

    public void parseSentence(ArrayList<String> lines,PrintWriter out,String ModelFileName)
        {
        int l = lines.size();
        if(l == 0)
            return;

        //logger.debug("tag, l == "+Integer.toString(l));
                                            //    0:ID 1:FORM 2:LEMMA 3:PLEMMA 4:POS 5:PPOS 6:FEAT 7:PFEAT 8:HEAD 9:PHEAD 10:DEPREL
                                            //   11:PDEPREL 12:FILLPRED 13:PRED 14:APREDs
        String[] forms = new String[l];     // 1
        String[] lemmas = new String[l];    // 2
        String[] plemmas = new String[l];   // 3
        String[] gpos = new String[l];      // 4
        String[] ppos = new String[l];      // 5
        String[] ofeats=new String[l];      // 7
        String[] pfeats=new String[l];      // 7
        int[]    heads = new int[l];        // 9
        String[] labels = new String[l];    // 11
        String[] fillp = new String[l];     // 12
        for(int k=0;k<l;k++) 
            {
            String[] values = lines.get(k).toString().split("\\t");
            int cols = values.length;
            if(cols > 1)
                {
                forms[k] = values[1];
                }
            else
                {
                forms[k] = "_";
                }
            lemmas[k] = "_";
            plemmas[k] = "_";
            gpos[k] = "_";
            ppos[k] = "_";
            ofeats[k] = "_";
            pfeats[k] = "_";
            heads[k] = -1;
            labels[k] = "_";
            fillp[k] = "_";
            }
            
        Tagger tagger = new Tagger(ModelFileName);
        SentenceData09 rawSentence = new SentenceData09(forms,plemmas,lemmas,gpos,ppos,labels,heads,fillp,ofeats,pfeats);
        SentenceData09 parsedSentence = tagger.tag(rawSentence);
        //logger.debug("syn: "+parsedSentence.toString());
        out.println(parsedSentence.toString());
        }

/**
Parse conll09 formatted input. It is important that the words are in the second
column (index 1) and the POS tags in the sixth (index 5). More than six columns
are not needed, but can be added. Unspecified strings must be written as _ and
unspecified integers as -1. The first line should contain the first word of the
first sentence, not ROOT. Each word is on a separate line. Sentences must be
separated by an empty line.

@param  arg the full path to the input file.
@param  out PrintWriter object to which output is written
@return     the parsed sentence, in conll09 format. The tenth column (index 11)
            contains the head, the twelfth column (index 13) the label.
*/
    public void tag(String arg,PrintWriter out,String ModelFileName)
    throws IOException    
        {
        //logger.debug("tag("+arg+","+ModelFileName+")");
        try {
            BufferedReader dis = new BufferedReader(new InputStreamReader(new FileInputStream(arg),"UTF8"));
            String str;
            ArrayList<String> lines = new ArrayList<String>();
            lines.add(new String("0\t<root>\t_\t_\t_\t<root-POS>"));
            while ((str = dis.readLine()) != null) 
                {
                if (str.trim().length() == 0) 
                    {
                    parseSentence(lines,out,ModelFileName);
                    lines.clear();
                    lines.add(new String("0\t<root>\t_\t_\t_\t<root-POS>"));
                    }
                else
                    {
                    lines.add(str);
                    //logger.debug("str: "+str);
                    }
                }
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(arg));
            if(lines.size() > 1)
                parseSentence(lines,out,ModelFileName);
            }
        catch(UnsupportedEncodingException ue)
            {
            logger.error("Not supported : "+ue.getMessage());
            }
        catch(IOException e)
            {
            logger.error("FileNotFoundException: "+e.getMessage());
            }
        }


    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
        {
        PrintWriter out = response.getWriter();
        out.println("GET not supported");
        }
 
     public static String getParmFromMultipartFormData(HttpServletRequest request,List<?> items,String parm)
        {
        //logger.debug("parm:["+parm+"]");
        String ret = "";
        try 
            {
            //logger.debug("items:"+items);
            Iterator<?> itr = items.iterator();
            //logger.debug("itr:"+itr);
            while(itr.hasNext()) 
                {
                //logger.debug("in loop");
                FileItem item = (FileItem)itr.next();
                if(item.isFormField()) 
                    {
                    //logger.debug("Field Name = "+item.getFieldName()+", String = "+item.getString());
                    if(item.getFieldName().equals(parm))
                        {
                        ret = item.getString();
                        //logger.debug("Found: " + parm + " = " + ret);
                        break; // currently not interested in other fields than parm
                        }
                    }
                }
            }
        catch(Exception ex) 
            {
            logger.error("uploadHandler.parseRequest Exception");
            }
        //logger.debug("value["+parm+"]="+ret);
        return ret;
        }
 
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException 
        {
        List<?> items = getParmList(request);
    //logger.debug("Calling getParmFromMultipartFormData");
        String language = getParmFromMultipartFormData(request,items,"model");
    //logger.debug("language "+language);
        request.setCharacterEncoding( "UTF-8" );
    //logger.debug("request.setCharacterEncodingDONE");
        response.setContentType("text/plain; charset=UTF-8");
    //logger.debug("response.setContentTypeDONE");
        response.setCharacterEncoding("UTF-8");
    //logger.debug("response.setCharacterEncodingDONE");
        response.setStatus(200);
    //logger.debug("response.setStatus");
        PrintWriter out = response.getWriter();
        //logger.debug("doPost, RemoteAddr == "+request.getRemoteAddr()+" language == "+language);

        java.lang.String arg  = getParmsAndFiles(items,response,out);
        //logger.debug("tag "+arg);

        String ModelFileName = modelFileName(language);
        //logger.debug("ModelFileName "+ModelFileName);
        tag(arg,out,ModelFileName);      
        }

    public static List<?> getParmList(HttpServletRequest request) throws ServletException
        {
        List<?> items = null;
        
        Enumeration<?> parmNames = request.getParameterNames();
        for (Enumeration<?> e = parmNames ; e.hasMoreElements() ;) 
            {
            String parmName = (String)e.nextElement();
            //logger.debug("parmName: " + parmName);            
            String vals[] = request.getParameterValues(parmName);
            for(int j = 0;j < vals.length;++j)
                {
                //logger.debug("value: " + vals[j]);            
                }
            }
        
        boolean is_multipart_formData = ServletFileUpload.isMultipartContent(request);

        if(is_multipart_formData)
            {
            DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();
            // Set the size threshold, above which content will be stored on disk.
            fileItemFactory.setSizeThreshold(1*1024*1024); //1 MB
            // Set the temporary directory to store the uploaded files of size above threshold.
            File tmpDir = new File(TMP_DIR_PATH);
            if(!tmpDir.isDirectory()) 
                {
                throw new ServletException("Trying to set \"" + TMP_DIR_PATH + "\" as temporary directory, but this is not a valid directory.");
                }
            fileItemFactory.setRepository(tmpDir);
            
            ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
            try {
                items = (List<?>)uploadHandler.parseRequest(request);
                }
            catch(FileUploadException ex) 
                {
                logger.error("Error encountered while parsing the request: "+ex.getMessage());
                }
            }
        return items;
        }

    public java.lang.String getParmsAndFiles(List<?> items,HttpServletResponse response,PrintWriter out) throws ServletException
        {        
        java.lang.String arg = "";

        try {
            // Parse the request
            Iterator<?> itr = items.iterator();
            while(itr.hasNext()) 
                {
                //logger.debug("in loop");
                FileItem item = (FileItem) itr.next();
                // Handle Form Fields.
                if(item.isFormField()) 
                    {
                    //logger.debug("form field:"+item.getName());                    
                    }
                else if(item.getName() != "")
                    {
                    //Handle Uploaded files.
                    String LocalFileName = item.getName();
                    //logger.debug("LocalFileName:"+LocalFileName);
                    // Write file to the ultimate location.

                    File tmpDir = new File(TMP_DIR_PATH);
                    if(!tmpDir.isDirectory()) 
                        {
                        throw new ServletException("Trying to set \"" + TMP_DIR_PATH + "\" as temporary directory, but this is not a valid directory.");
                        }

                    File file = File.createTempFile(LocalFileName,null,tmpDir);
                    String filename = file.getAbsolutePath();
                    //logger.debug("LocalFileName:"+filename);
                    item.write(file);
                    arg = filename;
                    }
                }
            }
        catch(Exception ex) 
            {
            }
        return arg;
        }
    }
