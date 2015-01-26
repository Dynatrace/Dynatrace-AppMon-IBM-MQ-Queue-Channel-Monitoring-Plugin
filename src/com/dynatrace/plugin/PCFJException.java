
package com.dynatrace.plugin;


public class PCFJException extends Exception {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String errorText;
    private int errorCode;

    public PCFJException(){	
 	   super();
    }
    
    public PCFJException(String arg ){
    	super(arg);
    }
    
    public PCFJException(Throwable th){
    	super(th);
    }
    
    public PCFJException(String arg,Throwable th){
    	super(arg,th);
    }
    
    public void setErrorCode(int errorCode){
    	this.errorCode = errorCode;
    }
    
    public int getErrorCode(){
    	return errorCode;
    }
    
    public void setErrorText(String errorText){
    	this.errorText = errorText;
    }
    
    public String getErrorText(){
      return errorText;	
    }
    
    
    
 
 

	
	
}
