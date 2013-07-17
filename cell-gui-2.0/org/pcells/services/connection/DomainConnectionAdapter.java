// $Id: DomainConnectionAdapter.java,v 1.3 2006/12/23 18:09:07 cvs Exp $
//
package org.pcells.services.connection ;
//
import java.util.*;
import java.io.* ;
import java.net.* ;
import org.pcells.services.connection.DomainConnection ;
import org.pcells.services.connection.DomainConnectionListener ;
import org.pcells.services.connection.DomainEventListener ;
import dmg.cells.applets.login.DomainObjectFrame ;
/**
 */
public class DomainConnectionAdapter implements DomainConnection {

     private HashMap   _packetHash = new HashMap() ;
     private Object    _ioLock     = new Object() ; 
     private int       _ioCounter  = 100 ;
     private ArrayList _listener   = new ArrayList() ;
     private boolean   _connected  = false ;
     
     private InputStream  _inputStream  = null ;
     private OutputStream _outputStream = null ;
     private Reader       _reader       = null ;
     private Writer       _writer       = null ;
     private ObjectOutputStream _objOut = null ;     
     private ObjectInputStream  _objIn  = null ;
     
     
     public String getAuthenticatedUser(){ return "Unknown" ; }
     public void setIoStreams( InputStream in , OutputStream out){
        setIoStreams( in , out, null , null ) ;
     } 
     public void setIoStreams( InputStream in , OutputStream out ,
                               Reader reader , Writer writer    ){
     
       _inputStream  = in ;
       _outputStream = out ;
       _reader       = reader ;
       _writer       = writer ;
       
     }
     public void go() throws Exception {
        //System.out.println("runConnection started");
        runConnection() ;
        //System.out.println("runConnection OK");
        
        informListenersOpened() ;

        //System.out.println("runReceiver starting");
        try{
           runReceiver() ;
        }finally{
           //System.out.println("runReceiver finished");
           informListenersClosed() ;
        }
      
     
     }
     public void close()throws IOException {
        _objOut.close() ;
     }
     private class MyFilter extends FilterInputStream {
        public MyFilter( InputStream in ){
          super(in);
        }
        public int read() throws IOException {
          int r = super.read() ;
          return r ;
        }
        public int read( byte [] data , int offset , int len ) throws IOException {
           int r = super.read( data , offset ,1 );
           return r;
        }
        public int read( byte [] data ) throws IOException {

           byte [] x = new byte[1];
           int r = super.read( x );
           data[0] = x[0];
           return r;
        }
     } 
     private void runConnection() throws IOException {
     
         InputStream inputstream = new MyFilter( _inputStream );
         BufferedReader reader = new BufferedReader(
                            _reader == null ?
                             new InputStreamReader(inputstream) :
                            _reader , 1 ) ;
                                 
         PrintWriter writer = new PrintWriter( _writer == null ?
                                               new OutputStreamWriter(_outputStream) :
                                               _writer ) ;
                              
         writer.println( "$BINARY$" ) ;
         writer.flush() ;
         String  check  = null ;
         do{
         
            check = reader.readLine()   ;  
            //System.out.println(" >>"+check+"<<");    

         }while( ! check.equals( "$BINARY$" )  ) ;
         //System.out.println("opening object streams");
         _objOut = new ObjectOutputStream( _outputStream ) ;
         //System.out.println("opening input object streams");
         _objIn  = new ObjectInputStream( inputstream)  ;

     }
     private void runReceiver() throws Exception {

        Object            obj   = null ;
        DomainObjectFrame frame = null ;
        DomainConnectionListener listener = null ;
        
        while( true ){
        
           if( ( obj = _objIn.readObject()  ) == null )break ;
           if( ! ( obj instanceof DomainObjectFrame ) )continue ;
           
           synchronized( _ioLock ){
           
              frame    = (DomainObjectFrame) obj ;
              listener = (DomainConnectionListener)_packetHash.remove( frame ) ;
              if( listener == null ){
                 System.err.println("Message without receiver : "+frame ) ;
                 continue ;
              }
           }
           try{
               listener.domainAnswerArrived( frame.getPayload() , frame.getSubId() ) ;
           }catch(Exception eee ){
               eee.printStackTrace();
               System.out.println( "Problem in domainAnswerArrived : "+eee ) ;
           }
        }
     }
     public int sendObject( Object obj , 
                            DomainConnectionListener listener ,
                            int id 
                                                 ) throws IOException {

         synchronized( _ioLock ){
         
             if( ! _connected )throw new IOException( "Not connected" ) ;
             
             DomainObjectFrame frame = 
                     new DomainObjectFrame( obj , ++_ioCounter , id ) ;
                     
             _objOut.writeObject( frame ) ;
             _objOut.reset() ;
             _packetHash.put( frame , listener ) ;
             return _ioCounter ;
         }
     }
     public int sendObject( String destination ,
                            Object obj , 
                            DomainConnectionListener listener ,
                            int id 
                                                 ) throws IOException {
//         System.out.println("Sending : "+obj ) ;
         synchronized( _ioLock ){
             if( ! _connected )throw new IOException( "Not connected" ) ;
             DomainObjectFrame frame = 
                     new DomainObjectFrame( destination , obj , ++_ioCounter , id ) ;
             _objOut.writeObject( frame ) ;
             _objOut.reset() ;
             _packetHash.put( frame , listener ) ;
             return _ioCounter ;
         }
     }
     public void addDomainEventListener( DomainEventListener listener ){
        synchronized( _ioLock ){
          _listener.add(listener) ;
          if( _connected ){
              try{  listener.connectionOpened( this ) ;
              }catch( Throwable t ){
                 t.printStackTrace() ;
              }
          }
        }
     }
     public void removeDomainEventListener( DomainEventListener listener ){
        synchronized( _ioLock ){
          _listener.remove(listener);
        }
     }
     private void informListenersOpened(){
        ArrayList array = (ArrayList)_listener.clone();
        synchronized( _ioLock ){
           _connected = true ;
           Iterator e = array.iterator() ;
           while( e.hasNext() ){
               DomainEventListener listener = (DomainEventListener)e.next() ;
               try{  listener.connectionOpened( this ) ;
               }catch( Throwable t ){
                  t.printStackTrace() ;
               }
           }
        }
     }
     private void informListenersClosed(){
        ArrayList array = (ArrayList)_listener.clone();
        synchronized( _ioLock ){
           _connected = false ;
           Iterator e = array.iterator() ;
           while( e.hasNext() ){
               DomainEventListener listener = (DomainEventListener)e.next() ;
               try{  listener.connectionClosed( this ) ;
               }catch( Throwable t ){
                  t.printStackTrace() ;
               }
           }
        }
     }

}
