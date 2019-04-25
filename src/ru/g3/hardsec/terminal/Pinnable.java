/**
 * 
 */
package ru.g3.hardsec.terminal;

/**
 * @author pspaendonck
 *
 */
public interface Pinnable {
	
	public byte[] enterPin();

	public void showSucces();

	public void showFailed();

	public void showBlocked();
}
