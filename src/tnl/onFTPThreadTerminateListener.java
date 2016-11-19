package tnl;



public interface onFTPThreadTerminateListener {
    public void onConnectionAutoTerminated(String connectionKey);
    public void onConnectionTerminated(String connectionKey);
}