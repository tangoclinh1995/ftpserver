/*
Name: TA Ngoc Linh
ID: 20213201
Email: nlta@connect.ust.hk
 */

package tnl;



public interface onFTPThreadTerminateListener {
    public void onConnectionAutoTerminated(String connectionKey);
    public void onConnectionTerminated(String connectionKey);
}