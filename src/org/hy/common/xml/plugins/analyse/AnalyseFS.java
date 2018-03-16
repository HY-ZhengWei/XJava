package org.hy.common.xml.plugins.analyse;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hy.common.Date;
import org.hy.common.Help;
import org.hy.common.StringHelp;
import org.hy.common.file.FileDataPacket;
import org.hy.common.file.FileHelp;
import org.hy.common.file.event.FileReadEvent;
import org.hy.common.file.event.FileReadListener;
import org.hy.common.net.ClientSocket;
import org.hy.common.net.ClientSocketCluster;
import org.hy.common.net.data.CommunicationResponse;
import org.hy.common.xml.annotation.Xjava;
import org.hy.common.xml.plugins.analyse.data.FileReport;





/**
 * Web文件资源管理器（支持集群）
 *
 * @author      ZhengWei(HY)
 * @createDate  2018-03-11
 * @version     v1.0
 */
@Xjava
public class AnalyseFS extends Analyse
{
    
    public static final String $WebHome   = "$WebHome";
    
    public static final String $CloudLock = ".cloudlock";
    
    
    
    /**
     * 显示指定目录下的所有文件及文件夹（支持集群）
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-11
     * @version     v1.0
     *
     * @param  i_BasePath        服务请求根路径。如：http://127.0.0.1:80/hy
     * @param  i_ObjectValuePath 对象值的详情URL。如：http://127.0.0.1:80/hy/../analyseObject?FS=Y&FP=xxx
     * @param  i_Cluster         是否为集群
     * @param  i_FPath           显示的目录路径
     * @param  i_SortType        排序类型
     * @return
     */
    @SuppressWarnings("unchecked")
    public String analysePath(String i_BasePath ,String i_ObjectValuePath ,boolean i_Cluster ,String i_FPath ,String i_SortType)
    {
        StringBuilder           v_Buffer  = new StringBuilder();
        int                     v_Index   = 0;
        String                  v_Content = this.getTemplateShowFilesContent();
        String                  v_FPath   = toWebHome(i_FPath);
        String                  v_AUrl    = "analyseObject?FS=Y" + (i_Cluster ? "&cluster=Y" : "") + "&S=" + i_SortType;
        int                     v_SCount  = 1;
        Map<String ,FileReport> v_Total   = null;
        
        // 本机统计
        if ( !i_Cluster )
        {
            v_Total = this.analysePath_Total(v_FPath);
        }
        // 集群统计
        else
        {
            List<ClientSocket> v_Servers = Cluster.getClusters();
            v_SCount = v_Servers.size();
            v_Total  = new HashMap<String ,FileReport>();
            
            if ( !Help.isNull(v_Servers) )
            {
                Map<ClientSocket ,CommunicationResponse> v_ResponseDatas = ClientSocketCluster.sendCommands(v_Servers ,Cluster.getClusterTimeout() ,"AnalyseFS" ,"analysePath_Total" ,new Object[]{v_FPath});
                
                for (Map.Entry<ClientSocket ,CommunicationResponse> v_Item : v_ResponseDatas.entrySet())
                {
                    CommunicationResponse v_ResponseData = v_Item.getValue();
                    
                    if ( v_ResponseData.getResult() == 0 )
                    {
                        if ( v_ResponseData.getData() != null && v_ResponseData.getData() instanceof Map )
                        {
                            Map<String ,FileReport> v_TempTotal = (Map<String ,FileReport>)v_ResponseData.getData();
                            
                            if ( !Help.isNull(v_TempTotal) )
                            {
                                for (Map.Entry<String ,FileReport> v_FR : v_TempTotal.entrySet())
                                {
                                    FileReport v_FReport = v_Total.get(v_FR.getKey());
                                    
                                    if ( v_FReport != null )
                                    {
                                        // 最后修改时间为：集群中的最后修改时间，才能保证多次刷新页面时，修改时间不会随机游走
                                        if ( v_FR.getValue().getLastTime().compareTo(v_FReport.getLastTime()) >= 1 )
                                        {
                                            v_FReport.setLastTime(v_FR.getValue().getLastTime());
                                        }
                                        v_FReport.getClusterHave().add(v_Item.getKey().getHostName());
                                    }
                                    else
                                    {
                                        v_FR.getValue().getClusterHave().add(v_Item.getKey().getHostName());
                                        v_Total.put(v_FR.getKey() ,v_FR.getValue());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        
        // 生成 .. 的跳转上一级目录
        Map<String ,String> v_RKey  = new HashMap<String ,String>();
        if ( !i_Cluster )
        {
            File v_File = new File(toTruePath(v_FPath));
            v_File = v_File.getParentFile();
            
            if ( v_File != null )
            {
                v_RKey.put(":FileName"  ,"<a href='" + v_AUrl + "&FP=" + toWebHome(v_File.getPath()) + "'>上一级目录</a>");
            }
            else
            {
                v_RKey.put(":FileName"  ,"<a href='" + v_AUrl + "&FP=" + v_FPath + "'>上一级目录</a>");
            }
        }
        else
        {
            String [] v_FPArr = v_FPath.split("/");
            if ( v_FPArr.length >= 2 && !v_FPath.endsWith("/.."))
            {
                v_RKey.put(":FileName"  ,"<a href='" + v_AUrl + "&FP=" + v_FPath.substring(0 ,v_FPath.length() - v_FPArr[v_FPArr.length-1].length() - 1) + "'>上一级目录</a>");
            }
            else
            {
                v_RKey.put(":FileName"  ,"<a href='" + v_AUrl + "&FP=" + v_FPath + "/..'>上一级目录</a>");
            }
        }
        v_RKey.put(":No"                ,String.valueOf(++v_Index));
        v_RKey.put(":LastTime"          ,"-");
        v_RKey.put(":FileType"          ,"文件夹");
        v_RKey.put(":FileSize"          ,"");
        v_RKey.put(":PromptClusterHave" ,"");
        v_RKey.put(":ClusterHave"       ,"-");
        v_RKey.put(":HIP"               ,"");
        v_RKey.put(":Operate"           ,"");
        v_Buffer.append(StringHelp.replaceAll(v_Content ,v_RKey));
        
        
        List<FileReport> v_FReports = Help.toList(v_Total);
        if ( "1".equalsIgnoreCase(i_SortType) )
        {
            // 按修改时间排序
            Help.toSort(v_FReports ,"directory Desc" ,"lastTime Desc" ,"fileNameToUpper");
        }
        else if ( "2".equalsIgnoreCase(i_SortType) )
        {
            // 按类型
            Help.toSort(v_FReports ,"directory Desc" ,"fileType" ,"fileNameToUpper" ,"lastTime Desc");
        }
        else if ( "3".equalsIgnoreCase(i_SortType) )
        {
            // 按大小排序
            Help.toSort(v_FReports ,"directory Desc" ,"fileSize NumDesc" ,"fileNameToUpper");
        }
        else
        {
            // 默认的：按名称排序
            Help.toSort(v_FReports ,"directory Desc" ,"fileNameToUpper" ,"lastTime Desc");
        }
        
        for (FileReport v_FReport : v_FReports)
        {
            v_RKey = new HashMap<String ,String>();
            
            v_RKey.put(":No"       ,String.valueOf(++v_Index));
            v_RKey.put(":LastTime" ,v_FReport.getLastTime());
            v_RKey.put(":FileType" ,v_FReport.getFileType());
            
            StringBuilder v_Operate    = new StringBuilder();
            String        v_FileNoName = v_Index + ":" + v_FReport.getFileName();
            if ( v_FReport.isDirectory() )
            {
                v_RKey.put(":FileName" ,"<a href='" + v_AUrl + "&FP=" + v_FReport.getFullName() + "'>" + v_FReport.getFileName() + "</a>");
                v_RKey.put(":FileSize" ,"<a href='#' onclick='calcFileSize(\"" + v_FileNoName + "\")'>计算</a>");
                
                v_Operate.append(StringHelp.lpad("" ,4 ,"&nbsp;")).append("<a href='#' onclick='cloneFile(\"").append(v_FileNoName).append("\")'>集群克隆</a>");
                v_Operate.append(StringHelp.lpad("" ,4 ,"&nbsp;")).append("<a href='#' onclick='zipFile(\"").append(v_FileNoName).append("\")'>压缩</a>");
                v_Operate.append(StringHelp.lpad("" ,4 ,"&nbsp;")).append("<a href='#' onclick='delFile(\"").append(v_FileNoName).append("\")'>删除</a>");
            }
            else
            {
                v_RKey.put(":FileName" ,v_FReport.getFileName()); 
                v_RKey.put(":FileSize" ,StringHelp.getComputeUnit(v_FReport.getFileSize()));
                
                v_Operate.append(StringHelp.lpad("" ,4 ,"&nbsp;")).append("<a href='#' onclick='cloneFile(\"").append(v_FileNoName).append("\")'>集群克隆</a>");
                
                String v_FType = v_FReport.getFileType().toLowerCase();
                if ( StringHelp.isContains(v_FType ,".zip" ,".tar" ,"gz") )
                {
                    v_Operate.append(StringHelp.lpad("" ,4 ,"&nbsp;")).append("<a href='#' onclick='unZipFile(\"").append(v_FileNoName).append("\")'>解压</a>");
                }
                else if ( StringHelp.isContains(v_FType ,".rar") )
                {
                    // Nothing. 没有解压能力的
                }
                else
                {
                    v_Operate.append(StringHelp.lpad("" ,4 ,"&nbsp;")).append("<a href='#' onclick='zipFile(\"").append(v_FileNoName).append("\")'>压缩</a>");
                }
                
                v_Operate.append(StringHelp.lpad("" ,4 ,"&nbsp;")).append("<a href='#' onclick='delFile(\"").append(v_FileNoName).append("\")'>删除</a>");
            }
            v_RKey.put(":Operate" ,v_Operate.toString());
            
            if ( !i_Cluster )
            {
                v_RKey.put(":PromptClusterHave" ,"");
                v_RKey.put(":ClusterHave"       ,"-");
                v_RKey.put(":HIP"               ,"");
            }
            else if ( v_FReport.getClusterHave().size() == v_SCount )
            {
                v_RKey.put(":PromptClusterHave" ,"");
                v_RKey.put(":ClusterHave"       ,"全有");
                v_RKey.put(":HIP"               ,"");
            }
            else
            {
                File v_File = new File(v_FReport.getFullName());
                if ( v_File.exists() )
                {
                    v_RKey.put(":ClusterHave" ,"<font color='red'>本服务有</font>");
                }
                else
                {
                    v_RKey.put(":ClusterHave" ,"<font color='red'>他机有</font>");
                }
                
                Help.toSort(v_FReport.getClusterHave());
                v_RKey.put(":PromptClusterHave" ,"资源存在的服务：\n\n" + StringHelp.toString(v_FReport.getClusterHave() ,"" ,"\n"));
                v_RKey.put(":HIP"               ,StringHelp.toString(v_FReport.getClusterHave() ,""));
            }
            
            v_Buffer.append(StringHelp.replaceAll(v_Content ,v_RKey));
        }
        
        /*
        v_Buffer.append(v_Content.replaceAll(":No"           ,String.valueOf(++v_Index))
                                 .replaceAll(":JobID"        ,"合计")
                                 .replaceAll(":IntervalType" ,"-")
                                 .replaceAll(":IntervalLen"  ,"-")
                                 .replaceAll(":LastTime"     ,"-")
                                 .replaceAll(":NextTime"     ,"-")
                                 .replaceAll(":JobDesc"      ,"Total: " + v_Total.getReports().size())
                       );
        */
        
        String v_Goto = StringHelp.lpad("" ,4 ,"&nbsp;");
        if ( i_Cluster )
        {
            v_Goto += "<a href='analyseObject?FS=Y&S=" + i_SortType +"&FP=" + v_FPath + "' style='color:#AA66CC'>查看本机</a>";
        }
        else
        {
            v_Goto += "<a href='analyseObject?FS=Y&S=" + i_SortType +"&cluster=Y&FP=" + v_FPath + "' style='color:#AA66CC'>查看集群</a>";
        }
        
        return StringHelp.replaceAll(this.getTemplateShowFiles()
                                    ,new String[]{":GotoTitle" ,":Title"          ,":HttpBasePath" ,":FPath" ,":Sort"    ,":cluster"             ,":Content"}
                                    ,new String[]{v_Goto       ,"Web文件资源管理器" ,i_BasePath      ,v_FPath  ,i_SortType ,(i_Cluster ? "Y" : "") ,v_Buffer.toString()});
    }
    
    
    
    /**
     * 本机显示指定目录下的所有文件及文件夹
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-11
     * @version     v1.0
     *
     * @return
     */
    public Map<String ,FileReport> analysePath_Total(String i_FPath)
    {
        Map<String ,FileReport> v_Ret   = new HashMap<String ,FileReport>();
        File                    v_FPath = new File(toTruePath(i_FPath));
        
        if ( v_FPath.isDirectory() )
        {
            File [] v_Files = v_FPath.listFiles();
            if ( !Help.isNull(v_Files) )
            {
                for (File v_File : v_Files)
                {
                    v_Ret.put(v_File.getName() ,new FileReport(i_FPath ,v_File));
                }
            }
        }
        
        return v_Ret;
    }
    
    
    
    /**
     * 克隆文件
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-14
     * @version     v1.0
     *
     * @param i_FilePath  路径
     * @param i_FileName  名称
     * @param i_HIP       资源存在的服务IP
     * @return            返回值：0.成功
     *                           1.异常
     *                           2.文件不存在
     */
    public String cloneFile(String i_FilePath ,String i_FileName ,String i_HIP)
    {
        File     v_File      = new File(toTruePath(i_FilePath) + Help.getSysPathSeparator() + i_FileName);
        File     v_CloudLock = null;
        FileHelp v_FileHelp  = new FileHelp();
        
        v_FileHelp.setBufferSize(1024 * 1024);
        v_FileHelp.setReturnContent(false);  // 不获取返回，可用于超大文件的读取
        
        if ( v_File.exists() )
        {
            try
            {
                v_CloudLock = new File(v_File.toString() + $CloudLock); 
                v_FileHelp.create(v_CloudLock.toString() ,Date.getNowTime().getFullMilli() ,"UTF-8");
                
                if ( v_File.isDirectory() )
                {
                    List<File> v_Files = v_FileHelp.getFiles(v_File);
                    if ( !Help.isNull(v_Files) )
                    {
                        String v_SaveFile = toTruePath(i_FilePath) + Help.getSysPathSeparator() + i_FileName + "_" + Date.getNowTime().getFullMilli_ID() + ".zip";
                        v_FileHelp.createZip(v_SaveFile ,v_File.getParent() ,v_Files ,true);
                    }
                    else
                    {
                        // 空目录时，可集群创建空目录即可，无须压缩目录
                    }
                }
                else
                {
                    v_FileHelp.addReadListener(new CloneListener(i_FilePath ,v_File ,v_FileHelp.getBufferSize() ,i_HIP));
                    v_FileHelp.getContentByte(v_File);
                }
                
                return StringHelp.replaceAll("{'retCode':'0'}" ,"'" ,"\"");
            }
            catch (Exception exce)
            {
                exce.printStackTrace();
            }
            finally
            {
                if ( v_CloudLock != null && v_CloudLock.exists() && v_CloudLock.isFile() )
                {
                    v_CloudLock.delete();
                }
            }
            
            return StringHelp.replaceAll("{'retCode':'1'}" ,"'" ,"\"");
        }
        
        return StringHelp.replaceAll("{'retCode':'2'}" ,"'" ,"\"");
    }
    
    
    
    /**
     * 集群克隆文件
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-14
     * @version     v1.0
     *
     * @param i_Dir
     * @param i_DataPacket
     * @return
     */
    public int cloneFileUpload(String i_Dir ,FileDataPacket i_DataPacket)
    {
        String v_Dir     = toTruePath(i_Dir);
        File   v_DirFile = new File(v_Dir);
        if ( !v_DirFile.exists() || !v_DirFile.isDirectory() )
        {
            // 如果目录不存在，则克隆到WebHome下。
            v_Dir = Help.getWebHomePath();
        }
        
        File     v_CloudLock = new File(v_Dir + Help.getSysPathSeparator() + i_DataPacket.getName() + $CloudLock);
        FileHelp v_FileHelp  = new FileHelp();
        
        try
        {
            if ( !v_CloudLock.exists() )
            {
                if ( i_DataPacket.getDataNo().intValue() == 1 )
                {
                    File v_Old = new File(v_Dir + Help.getSysPathSeparator() + i_DataPacket.getName());
                    if ( v_Old.exists() && v_Old.isFile() )
                    {
                        v_Old.delete();
                    }
                }
                
                return v_FileHelp.uploadServer(v_Dir ,i_DataPacket);
            }
            else
            {
                // 克隆的原文件，不再二次克隆
            }
        }
        catch (Exception exce)
        {
            exce.printStackTrace();
        }
        
        return FileHelp.$Upload_Error;
    }
    
    
    
    /**
     * 删除本地文件
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-13
     * @version     v1.0
     *
     * @param i_FilePath  路径
     * @param i_FileName  名称
     * @return            返回值：0.成功
     *                           1.异常
     *                           2.文件不存在
     */
    public String delFile(String i_FilePath ,String i_FileName)
    {
        File v_File = new File(toTruePath(i_FilePath) + Help.getSysPathSeparator() + i_FileName);
        
        if ( v_File.exists() )
        {
            try
            {
                if ( v_File.isDirectory() )
                {
                    FileHelp v_FileHelp = new FileHelp();
                    v_FileHelp.delFiles(v_File ,Date.getNowTime() ,true);
                    v_File.delete();
                }
                else
                {
                    v_File.delete();
                }
                
                return StringHelp.replaceAll("{'retCode':'0'}" ,"'" ,"\"");
            }
            catch (Exception exce)
            {
                exce.printStackTrace();
            }
            
            return StringHelp.replaceAll("{'retCode':'1'}" ,"'" ,"\"");
        }
        
        return StringHelp.replaceAll("{'retCode':'2'}" ,"'" ,"\"");
    }
    
    
    
    /**
     * 集群删除文件
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-15
     * @version     v1.0
     *
     * @param i_FilePath
     * @param i_FileName
     * @param i_HIP
     * @return            返回值：0.成功
     *                           1.异常，同时返回失效服务器的IP。
     */
    public String delFileByCluster(String i_FilePath ,String i_FileName ,String i_HIP)
    {
        String             v_HIP     = "";
        int                v_ExecRet = 0;
        List<ClientSocket> v_Servers = Cluster.getClusters();
        
        removeHIP(v_Servers ,i_HIP ,false);
        
        if ( !Help.isNull(v_Servers) )
        {
            Map<ClientSocket ,CommunicationResponse> v_ResponseDatas = ClientSocketCluster.sendCommands(v_Servers ,Cluster.getClusterTimeout() ,"AnalyseFS" ,"delFile" ,new Object[]{i_FilePath ,i_FileName});
            
            for (Map.Entry<ClientSocket ,CommunicationResponse> v_Item : v_ResponseDatas.entrySet())
            {
                CommunicationResponse v_ResponseData = v_Item.getValue();
                
                if ( v_ResponseData.getResult() == 0 )
                {
                    if ( v_ResponseData.getData() != null )
                    {
                        String v_RetValue = v_ResponseData.getData().toString();
                        v_RetValue = StringHelp.replaceAll(v_RetValue ,"\"" ,"'");
                        
                        if ( StringHelp.isContains(v_RetValue ,"'retCode':'0'") )
                        {
                            v_ExecRet++;
                        }
                        else if ( StringHelp.isContains(v_RetValue ,"'retCode':'1'") )
                        {
                            if ( !Help.isNull(v_HIP) )
                            {
                                v_HIP += ",";
                            }
                            v_HIP += v_Item.getKey().getHostName();
                        }
                        else if ( StringHelp.isContains(v_RetValue ,"'retCode':'2'") )
                        {
                            v_ExecRet++;
                        }
                    }
                }
            }
        }
        
        if ( v_ExecRet == v_Servers.size() )
        {
            return StringHelp.replaceAll("{'retCode':'0'}" ,"'" ,"\"");
        }
        else
        {
            return StringHelp.replaceAll("{'retCode':'1','retHIP':'" + v_HIP + "'}" ,"'" ,"\"");
        }
    }
    
    
    
    /**
     * 压缩文件
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-13
     * @version     v1.0
     *
     * @param i_FilePath
     * @param i_FileName
     * @param i_TimeID    时间ID
     * @return            返回值：0.成功
     *                           1.异常
     *                           2.文件不存在
     *                           3.压缩目录中无任何文件，即空目录
     */
    public String zipFile(String i_FilePath ,String i_FileName ,String i_TimeID)
    {
        File       v_File     = new File(toTruePath(i_FilePath) + Help.getSysPathSeparator() + i_FileName);
        FileHelp   v_FileHelp = new FileHelp();
        List<File> v_Files    = new ArrayList<File>();
        
        if ( v_File.exists() )
        {
            try
            {
                String v_SaveFile = toTruePath(i_FilePath) + Help.getSysPathSeparator() + i_FileName + "_" + i_TimeID + ".zip";
                if ( v_File.isDirectory() )
                {
                    v_Files = v_FileHelp.getFiles(v_File);
                    if ( !Help.isNull(v_Files) )
                    {
                        v_FileHelp.createZip(v_SaveFile ,v_File.getParent() ,v_Files ,true);
                    }
                    else
                    {
                        return StringHelp.replaceAll("{'retCode':'3'}" ,"'" ,"\"");
                    }
                }
                else
                {
                    v_Files.add(v_File);
                    v_FileHelp.createZip(v_SaveFile ,null ,v_Files ,true);
                }
                
                return StringHelp.replaceAll("{'retCode':'0'}" ,"'" ,"\"");
            }
            catch (Exception exce)
            {
                exce.printStackTrace();
            }
            
            return StringHelp.replaceAll("{'retCode':'1'}" ,"'" ,"\"");
        }
        
        return StringHelp.replaceAll("{'retCode':'2'}" ,"'" ,"\"");
    }
    
    
    
    /**
     * 集群压缩文件
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-15
     * @version     v1.0
     *
     * @param i_FilePath
     * @param i_FileName
     * @param i_HIP
     * @return            返回值：0.成功
     *                           1.异常，同时返回失效服务器的IP。
     */
    public String zipFileByCluster(String i_FilePath ,String i_FileName ,String i_HIP)
    {
        String             v_HIP     = "";
        int                v_ExecRet = 0;
        List<ClientSocket> v_Servers = Cluster.getClusters();
        
        removeHIP(v_Servers ,i_HIP ,false);
        
        if ( !Help.isNull(v_Servers) )
        {
            Map<ClientSocket ,CommunicationResponse> v_ResponseDatas = ClientSocketCluster.sendCommands(v_Servers ,Cluster.getClusterTimeout() ,"AnalyseFS" ,"zipFile" ,new Object[]{i_FilePath ,i_FileName ,Date.getNowTime().getFullMilli_ID()});
            
            for (Map.Entry<ClientSocket ,CommunicationResponse> v_Item : v_ResponseDatas.entrySet())
            {
                CommunicationResponse v_ResponseData = v_Item.getValue();
                
                if ( v_ResponseData.getResult() == 0 )
                {
                    if ( v_ResponseData.getData() != null )
                    {
                        String v_RetValue = v_ResponseData.getData().toString();
                        v_RetValue = StringHelp.replaceAll(v_RetValue ,"\"" ,"'");
                        
                        if ( StringHelp.isContains(v_RetValue ,"'retCode':'0'") )
                        {
                            v_ExecRet++;
                        }
                        else if ( StringHelp.isContains(v_RetValue ,"'retCode':'1'") )
                        {
                            if ( !Help.isNull(v_HIP) )
                            {
                                v_HIP += ",";
                            }
                            v_HIP += v_Item.getKey().getHostName();
                        }
                        else if ( StringHelp.isContains(v_RetValue ,"'retCode':'2'") )
                        {
                            v_ExecRet++;
                        }
                    }
                }
            }
        }
        
        if ( v_ExecRet == v_Servers.size() )
        {
            return StringHelp.replaceAll("{'retCode':'0'}" ,"'" ,"\"");
        }
        else
        {
            return StringHelp.replaceAll("{'retCode':'1','retHIP':'" + v_HIP + "'}" ,"'" ,"\"");
        }
    }
    
    
    
    /**
     * 解压文件
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-13
     * @version     v1.0
     *
     * @param i_FilePath
     * @param i_FileName
     * @return            返回值：0.成功
     *                           1.异常
     *                           2.文件不存在
     */
    public String unZipFile(String i_FilePath ,String i_FileName)
    {
        File       v_File     = new File(toTruePath(i_FilePath) + Help.getSysPathSeparator() + i_FileName);
        FileHelp   v_FileHelp = new FileHelp();
        
        if ( v_File.exists() && v_File.isFile() )
        {
            try
            {
                v_FileHelp.setOverWrite(true);
                v_FileHelp.UnCompressZip(v_File.toString() ,v_File.getParent() ,true);
                
                return StringHelp.replaceAll("{'retCode':'0'}" ,"'" ,"\"");
            }
            catch (Exception exce)
            {
                exce.printStackTrace();
            }
            
            return StringHelp.replaceAll("{'retCode':'1'}" ,"'" ,"\"");
        }
        
        return StringHelp.replaceAll("{'retCode':'2'}" ,"'" ,"\"");
    }
    
    
    
    /**
     * 集群解压文件
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-15
     * @version     v1.0
     *
     * @param i_FilePath
     * @param i_FileName
     * @param i_HIP
     * @return            返回值：0.成功
     *                           1.异常，同时返回失效服务器的IP。
     */
    public String unZipFileByCluster(String i_FilePath ,String i_FileName ,String i_HIP)
    {
        String             v_HIP     = "";
        int                v_ExecRet = 0;
        List<ClientSocket> v_Servers = Cluster.getClusters();
        
        removeHIP(v_Servers ,i_HIP ,false);
        
        if ( !Help.isNull(v_Servers) )
        {
            Map<ClientSocket ,CommunicationResponse> v_ResponseDatas = ClientSocketCluster.sendCommands(v_Servers ,Cluster.getClusterTimeout() ,"AnalyseFS" ,"unZipFile" ,new Object[]{i_FilePath ,i_FileName});
            
            for (Map.Entry<ClientSocket ,CommunicationResponse> v_Item : v_ResponseDatas.entrySet())
            {
                CommunicationResponse v_ResponseData = v_Item.getValue();
                
                if ( v_ResponseData.getResult() == 0 )
                {
                    if ( v_ResponseData.getData() != null )
                    {
                        String v_RetValue = v_ResponseData.getData().toString();
                        v_RetValue = StringHelp.replaceAll(v_RetValue ,"\"" ,"'");
                        
                        if ( StringHelp.isContains(v_RetValue ,"'retCode':'0'") )
                        {
                            v_ExecRet++;
                        }
                        else if ( StringHelp.isContains(v_RetValue ,"'retCode':'1'") )
                        {
                            if ( !Help.isNull(v_HIP) )
                            {
                                v_HIP += ",";
                            }
                            v_HIP += v_Item.getKey().getHostName();
                        }
                        else if ( StringHelp.isContains(v_RetValue ,"'retCode':'2'") )
                        {
                            v_ExecRet++;
                        }
                    }
                }
            }
        }
        
        if ( v_ExecRet == v_Servers.size() )
        {
            return StringHelp.replaceAll("{'retCode':'0'}" ,"'" ,"\"");
        }
        else
        {
            return StringHelp.replaceAll("{'retCode':'1','retHIP':'" + v_HIP + "'}" ,"'" ,"\"");
        }
    }
    
    
    
    /**
     * 计算目录的大小
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-14
     * @version     v1.0
     *
     * @param i_FilePath  路径
     * @param i_FileName  名称
     * @return            返回值：0.成功
     *                           1.异常
     *                           2.文件不存在
     */
    public String calcFileSize(String i_FilePath ,String i_FileName)
    {
        File v_File = new File(toTruePath(i_FilePath) + Help.getSysPathSeparator() + i_FileName);
        
        if ( v_File.exists() )
        {
            try
            {
                if ( v_File.isDirectory() )
                {
                    FileHelp v_FileHelp = new FileHelp();
                    long     v_Size     = v_FileHelp.calcSize(v_File);
                    return StringHelp.replaceAll("{'retCode':'0','fileSize':'" + StringHelp.getComputeUnit(v_Size) + "'}" ,"'" ,"\"");
                }
            }
            catch (Exception exce)
            {
                exce.printStackTrace();
            }
            
            return StringHelp.replaceAll("{'retCode':'1'}" ,"'" ,"\"");
        }
        
        return StringHelp.replaceAll("{'retCode':'2'}" ,"'" ,"\"");
    }
    
    
    
    /**
     * 转为 $WebHome 字符的路径
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-12
     * @version     v1.0
     *
     * @param i_Path
     * @return
     */
    public static String toWebHome(String i_Path)
    {
        String v_WebHome = StringHelp.replaceAll(Help.getWebHomePath() ,"\\" ,"/");
        if ( v_WebHome.endsWith("/") )
        {
            v_WebHome = v_WebHome.substring(0 ,v_WebHome.length() - 1);
        }
        
        String v_Ret = StringHelp.replaceAll(i_Path ,"\\" ,"/");
        v_Ret = StringHelp.replaceAll(v_Ret ,v_WebHome ,$WebHome);
        
        if ( v_Ret.endsWith($WebHome) )
        {
            return $WebHome;
        }
        else
        {
            return v_Ret;
        }
    }
    
    
    
    /**
     * 将  $WebHome 字符的路径，转为真实的本地路径
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-12
     * @version     v1.0
     *
     * @param i_Path
     * @return
     */
    public static String toTruePath(String i_Path)
    {
        String v_WebHome = StringHelp.replaceAll(Help.getWebHomePath() ,"\\" ,"/");
        if ( v_WebHome.endsWith("/") )
        {
            v_WebHome = v_WebHome.substring(0 ,v_WebHome.length() - 1);
        }
        
        String v_Ret = StringHelp.replaceAll(i_Path ,"/" ,Help.getSysPathSeparator());
        v_Ret = StringHelp.replaceAll(v_Ret ,$WebHome ,v_WebHome);
        return v_Ret;
    }
    
    
    
    /**
     * 1.   删除已有资源的服务器信息
     * 2. 或删除没有资源的服务器信息
     * 
     * @author      ZhengWei(HY)
     * @createDate  2018-03-15
     * @version     v1.0
     *
     * @param io_Servers
     * @param i_HIP
     * @param i_IsRemoveHave  有资源时删除服务器，还是无资源时删除服务器
     */
    public static void removeHIP(List<ClientSocket> io_Servers ,String i_HIP ,boolean i_IsRemoveHave)
    {
        if ( Help.isNull(io_Servers) )
        {
            return;
        }
        
        if ( !Help.isNull(i_HIP) )
        {
            String v_HIP = i_HIP + ",";
            
            if ( i_IsRemoveHave )
            {
                for (int i=io_Servers.size()-1; i>=0; i--)
                {
                    if ( v_HIP.indexOf(io_Servers.get(i).getHostName() + ",") >= 0 )
                    {
                        // 删除有资源的服务器，对没有资源的服务进行操作
                        io_Servers.remove(i);
                    }
                }
            }
            else
            {
                for (int i=io_Servers.size()-1; i>=0; i--)
                {
                    if ( v_HIP.indexOf(io_Servers.get(i).getHostName() + ",") < 0 )
                    {
                        // 删除没有资源的服务器，对有资源的服务进行操作
                        io_Servers.remove(i);
                    }
                }
            }
        }
    }
    
    
    
    private String getTemplateShowFiles()
    {
        return this.getTemplateContent("template.showFiles.html");
    }
    
    
    
    private String getTemplateShowFilesContent()
    {
        return this.getTemplateContent("template.showFilesContent.html");
    }
    
    
    
    
    
    /**
     * 克隆文件的监听器
     *
     * @author      ZhengWei(HY)
     * @createDate  2018-03-15
     * @version     v1.0
     */
    class CloneListener implements FileReadListener
    {
        
        private String             savePath;
        
        private FileDataPacket     dataPacket;
        
        private List<ClientSocket> servers;
        
        
        
        public CloneListener(String i_SavePath ,File i_File ,int i_BufferSize ,String i_HIP)
        {
            this.savePath   = i_SavePath;
            this.dataPacket = new FileDataPacket();
            this.dataPacket.setName(     i_File.getName());
            this.dataPacket.setDataCount((int)Math.ceil(Help.division(i_File.length() , i_BufferSize)));
            this.dataPacket.setDataNo(0);
            
            this.servers = Cluster.getClusters();
            removeHIP(this.servers ,i_HIP ,true);
        }
        
        
        
        /**
         * 读取文件内容之前
         * 
         * @param e
         * @return   返回值表示是否继续拷贝
         */
        public boolean readBefore(FileReadEvent i_Event)
        {
            return true;
        }
        
        

        /**
         * 读取文件内容的进度
         * 
         * @param e
         * @return   返回值表示是否继续拷贝
         */
        public boolean readProcess(FileReadEvent i_Event)
        {
            if ( Help.isNull(this.servers) )
            {
                return false;
            }
            
            this.dataPacket.setDataNo(this.dataPacket.getDataNo() + 1);
            this.dataPacket.setDataByte(i_Event.getDataByte());
            
            Map<ClientSocket ,CommunicationResponse> v_ResponseDatas = ClientSocketCluster.sendCommands(this.servers ,Cluster.getClusterTimeout() ,"AnalyseFS" ,"cloneFileUpload" ,new Object[]{this.savePath ,this.dataPacket});
            
            for (Map.Entry<ClientSocket ,CommunicationResponse> v_Item : v_ResponseDatas.entrySet())
            {
                CommunicationResponse v_ResponseData = v_Item.getValue();
                
                if ( v_ResponseData.getResult() == 0 )
                {
                    if ( v_ResponseData.getData() != null )
                    {
                        int v_UploadValue = (Integer)v_ResponseData.getData();
                        
                        if ( v_UploadValue == FileHelp.$Upload_Finish )
                        {
                            
                        }
                        else if ( v_UploadValue == FileHelp.$Upload_GoOn )
                        {
                            
                        }
                        else 
                        {
                            
                        }
                    }
                }
            }
            
            return true;
        }
        
        
        
        /**
         * 读取文件内容完成之后
         * 
         * @param e
         */
        public void readAfter(FileReadEvent i_Event)
        {
            
        }
        
    }
    
}
