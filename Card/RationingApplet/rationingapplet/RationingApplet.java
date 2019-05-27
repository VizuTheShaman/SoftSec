package rationingapplet;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;

public class RationingApplet extends Applet implements ISO7816 {
    // Data definitions
    //private byte someData[];
    private byte notepad[];
    private short oldState[];
    private short sequenceNumber[];
    private byte terminalType[];
    private RSAPrivateKey cardPrivateKey;
    private RSAPublicKey terminalPublicKey[];
    private RSAPublicKey masterKey[];
    private AESKey symmetricKey[];
    private byte cardCertificate[];
    private Cipher rSACipher;
    private Cipher aESCipher;
    private RandomData rngesus;
    private byte cardNumber[];
    private static short RSA_KEY_BYTESIZE = 128;
    private static short AES_KEY_BYTESIZE = 16; // 128/8
    private static short CERTIFICATE_BYTESIZE = 130;
    private static short HANDSHAKE_ONE_INPUT_LENGTH_MIN = 135;
    private static byte VERSION_NUMBER = 1;

    public RationingApplet() {
        //Do data allocations here.
        //someData = new byte[10]; // persistent data, stays on the card between resets
        //someData = JCSystem.makeTransientByteArray((short) 10, JCSystem.CLEAR_ON_RESET); // transient data, is cleared when the card is removed from the terminal.

        //260 bytes of memory that should be used as temporary storage of byte arrays instead of defining different arrays for every single thing.
        notepad = JCSystem.makeTransientByteArray((short) 260, JCSystem.CLEAR_ON_RESET);

        //TODO get all the upcoming variables from personalizing
        masterKey = (RSAPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PUBLIC, KeyBuilder.LENGTH_RSA_1024, false);
//        masterKey.setExponent(buffer, offset, length);
//        masterKey.setModulus(buffer, offset, length);
        cardPrivateKey = (RSAPrivateKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PRIVATE, KeyBuilder.LENGTH_RSA_1024, false);
//        cardPrivateKey.setExponent(buffer, offset, length);
//        cardPrivateKey.setModulus(buffer, offset, length);
        cardCertificate = new byte[130];
        cardNumber = new byte[4];


        rSACipher = Cipher.getInstance(Cipher.ALG_RSA_PKCS1, false);
        //AES algorithm:

        rngesus = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);

        oldState = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_RESET);
        sequenceNumber = JCSystem.makeTransientShortArray((short) 2, JCSystem.CLEAR_ON_RESET); // The sequence number and how much is added per increment.

        // Handshake step 1
        terminalType = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_RESET);
        terminalPublicKey = (RSAPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PUBLIC, KeyBuilder.LENGTH_RSA_1024, false);

		// Finally, register the applet.
        register();
    }

    /**
     * Called by the JavaCard OS when the applet is selected. Can be overwritted to be used for initializations
     * @return True if the selection of the applet was successful, false otherwise
     */
    public boolean select() {
        oldState[0] = (short) 0;
        sequenceNumber[0] = (short) 0;
        return true;
    }

    /*public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
        return null;
    }*/

    /**
     * Called when the applet is installed on the card, note that the data allocation currently performed in the
     * constructor can be done here instead. In that case, do not forget to call the register()-method of the app.
     * See also the JavaCard API documentation for javacard.framework.RationingApplet.install()
     * @param bArray The array containing installation parameter
     * @param bLength The starting offset in bArray
     * @param bOffset The length in bytes of the parameter data in bArray
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new RationingApplet();
    }

    /**
     * Called whenever the terminal sends an APDU message to the card, this includes the messages that opens (selects)
     * the applet from the OS.
     * @param apdu The data sent to the card
     */
    public void process(APDU apdu) {
        // Get the byte array from the received APDU, this includes the header.
        byte[] buffer = apdu.getBuffer();
        // Find out how long the incoming data is in bytes (the 5th byte of the APDU buffer)
        byte dataLength = buffer[OFFSET_CDATA];

        short terminalState = Util.makeShort(buffer[OFFSET_P1], buffer[OFFSET_P2]);

        switch(terminalState) {
            case 1:
                if (oldState[0] != (short) 0) {
                    throw new ISOException((short) 0); // Maybe we should define a StateException or something, or can JavaCard not handle that?
                }
                buffer = handshakeStepOne(buffer, dataLength);

                break;
            case 7:
                 buffer = personalizeStepOne(buffer, dataLength);

            break;
            default:
                throw new ISOException((short) 0); // No idea what value should be passed
        }



        // Extract the returnLength from the apdu buffer (the last byte of the APDU buffer).
        // This is also information is also returned by apdu.setOutgoing(), so I've commented it out here.
        //byte returnLength = buffer[(short)(OFFSET_CDATA + dataLength+1)];

        // Extract the incoming data into a byte array on the card.
        // This is not a necessary step, it might be more memory efficient to not do this, and to extract data from the
        // buffer itself when it is needed. In that case we might have to do some bound checking, I don't know what
        // happens if we request bytes outside of the received input.
        /*byte[] data = new byte[dataLength];
        for (short i = 0; i < dataLength; i++) {
            data[i] = buffer[(short)(OFFSET_CDATA + i+1)];
        }*/

        // process() is also called with the APDU that selected this applet in the first place, ignore that APDU, it
        // has done what it had to do.
        if (selectingApplet()) {
            return;
        }

        // Set the apdu to outgoing, discards any remaining input data. This also returns the expected output length.
        // I'm not sure what happens if there is no expected response, I expect returnLength to be 0 then (?)
        short returnLength = apdu.setOutgoing();
        /*if (returnLength < 5) {
            ISOException.throwIt((short) (SW_WRONG_LENGTH | 5));
        }*/

        // Set the length of the byte array we will return. This seems a bit redundant, since we also request this
        // information from the APDU, but we can performs checks on the expected return length like this, I guess.
        apdu.setOutgoingLength(returnLength);

        // We can now edit the buffer we initially received from the APDU to add our response.
//        buffer[0] = data[0];

        /*
        JavaCard also provides some utility methods to add larger data structures into a byte array, like the setShort()
        call under this comment, which adds the short provided in the third parameter to the byte array in the first
        parameter, starting at the position given in the second parameter.
         */
        //Util.setShort(buffer, (short) 0, (short) 25);

        // apdu.sendBytes(offset, length): Send <length> bytes off the APDU buffer starting from <offset>, note that
        // this method can be called multiple times for a single response of the card. See the JavaCard API docs:
        // javacard.framework.APDU.sendBytes()
        apdu.sendBytes((short) 0, returnLength);
    }

    private byte[] handshakeStepOne (byte[] buffer, byte dataLength) {
        // Check if the number of bytes in the APDU is not smaller than the minimum number required for this step.
        if (buffer[OFFSET_CDATA] < HANDSHAKE_ONE_INPUT_LENGTH_MIN) {
            throw new ISOException((short) 0); //TODO how do I exception?
        }

        // Extract the type of terminal we're talking to, store this to determine what protocol to switch to afterwards.
        terminalType[0] = buffer[OFFSET_CDATA + 2];

        byte terminalSupportedVersionsLength = buffer[OFFSET_CDATA + 3];

        // Check if the supported version length fits in the data (why don't we just calculate this length value?)
        if ((short) ((short) terminalSupportedVersionsLength + CERTIFICATE_BYTESIZE + (short) 4) != (short) dataLength) {
            throw new ISOException((short) 0); //TODO how do I exception?
        }

        // Check if the list of supported versions includes our version.
        boolean supported = false;
        for (byte i = OFFSET_CDATA+4; i < terminalSupportedVersionsLength; i++) {
            if (buffer[i] == VERSION_NUMBER) {
                supported = true;
            }
        }
        if (!supported) {
            throw new ISOException((short) 0); //TODO maybe some handling here instead?
        }

        sequenceNumber[0] = Util.makeShort(buffer[(short) (OFFSET_CDATA + terminalSupportedVersionsLength + (short) 2)],
                buffer[(short) (OFFSET_CDATA + terminalSupportedVersionsLength + (short) 3)]);

        for (short i = 0; i < CERTIFICATE_BYTESIZE; i++) {
            notepad[i] = buffer[(short) (i + OFFSET_CDATA + terminalSupportedVersionsLength + (short) 4)];
        }

        // Decrypt the certificate with the master key.
        rSACipher.init(masterKey, Cipher.MODE_DECRYPT);
        rSACipher.doFinal(notepad, (short) 0, CERTIFICATE_BYTESIZE, notepad, CERTIFICATE_BYTESIZE);

        terminalPublicKey.setExponent(notepad, (short) 130, (short) (RSA_KEY_BYTESIZE / (short) 2));
        terminalPublicKey.setModulus(notepad, (short) ((short) 130 + (RSA_KEY_BYTESIZE/(short)2)), (short) (RSA_KEY_BYTESIZE / (short) 2));

        // Start building the response
        // Add the card number
        for (short i = 0; i < 4; i++) {
            buffer[i] = cardNumber[i];
        }

        // Add the version number
        buffer[4] = VERSION_NUMBER;

        // Generate sequence number increment and apply it
        rngesus.generateData(notepad, (short) 0, (short) 2);
        sequenceNumber[1] = (short) (Util.makeShort(notepad[0], notepad[1]) % (short) 2^15);
        sequenceNumber[0] = (short) ((short) (sequenceNumber[0] + sequenceNumber[1]) % (short) 2^15);

        rSACipher.init(cardPrivateKey, Cipher.MODE_ENCRYPT);
        Util.setShort(notepad, (short) 0, sequenceNumber[0]);
        rSACipher.doFinal(notepad, (short) 0, (short) 2, notepad, (short) 2);
        buffer[5] = notepad[2];
        buffer[6] = notepad[3];

        for (short i = 0; i < CERTIFICATE_BYTESIZE; i++) {
            buffer[(short) (i + 7)] = cardCertificate[i];
        }

        return buffer;
    }

    private byte[] HandshakeStepThree (byte[] buffer) {
        // In this step the card receives a symmetric key and a sequence number + a random increment * 2.
        // The symmetric key is generated so all communication between card and terminal remains confidential.
        // To let the card know about this key, it is paired with the incremented sequence number (seq. nr. + 3*increment)
        // and encrypted with the private terminal key and the public card key, and sent to the card.
        // Upon receiving this, the card should decrypt it using their own private key and the public terminal key.

        // RECEIVED MESSAGE

        // 1. Decrypt using private key and public terminal key

        for (byte i = 0; i<AES_KEY_BYTESIZE+2; i++){
            notepad[i] = buffer[OFFSET_CDATA+2];
        }

        rSACipher.init(cardPrivateKey,Cipher.MODE_DECRYPT);
        rSACipher.doFinal(notepad,(short)0,AES_KEY_BYTESIZE+2,notepad,AES_KEY_BYTESIZE+2);

        rSACipher.init(terminalPublicKey,Cipher.MODE_DECRYPT);
        rSACipher.doFinal(notepad,(short)0,AES_KEY_BYTESIZE+2,notepad,AES_KEY_BYTESIZE+2);

        // 2. Check sequence number (given that randIncr is stored)
        short[] receivedSequence = Util.makeShort(notepad[AES_KEY_BYTESIZE], notepad[AES_KEY_BYTESIZE+1]);

        if (receivedSequence != (sequenceNumber[0] + 2*sequenceNumber[1])%(2^15)){
            throw new CardException((short) 0); // placeholder error
        }

        // 3. Store symmetric key
        byte[] symm;
        for (byte i = 0; i<AES_KEY_BYTESIZE; i++){
            symm[i] = notepad[i];
        }
        // save it as a AESKey symmetricKey afterwards, not yet sure how exactly this is done

        // PREPARE RESPONSE

        // The card will respond with a symmetric encrypted (aesKey) OK in handshake step four.

        short[] incremented = (sequenceNumber[0] + 3*sequenceNumber[1])%(2^15);
        Util.setShort(buffer,(short)0,incremented);

        aESCipher.init(symmetricKey,Cipher.MODE_ENCRYPT);
        aESCipher.doFinal(buffer,(short)0,short(2),buffer,(short)0,(short)2);
        
        return null; //debug return statement
    }

    private byte[] personalizeStepOne (byte[] buffer, byte dataLength) {
        // Private key (128 bytes), Pin (4 bytes), Cardnumber (4 bytes)
        cardPrivateKey.setExponent(buffer, OFFSET_CDATA, (short) (RSA_KEY_BYTESIZE / (short) 2));
        cardPrivateKey.setModulus(buffer, (short) (OFFSET_CDATA + (short) (RSA_KEY_BYTESIZE / (short) 2)), (short) (OFFSET_CDATA + RSA_KEY_BYTESIZE);

        //TODO actually configure the PIN here.
        for (short i = 0; i < 4; i++) {
            notepad[i] = buffer[(short) (OFFSET_CDATA + RSA_KEY_BYTESIZE + i)];
        }

        for (short i = 0; i < 4; i++) {
            cardNumber[i] = buffer[(short) (OFFSET_CDATA + RSA_KEY_BYTESIZE + i + 4)];
        }

        //TODO response
        // Response (1 byte)
    }

    private byte[] personalizeStepTwo (byte[] buffer, byte dataLength) {
        // Master key (128 bytes)
        masterKey.setExponent(buffer, OFFSET_CDATA, (short) (RSA_KEY_BYTESIZE / (short) 2));
        masterKey.setModulus(buffer, (short) (OFFSET_CDATA + (short) (RSA_KEY_BYTESIZE / (short) 2)), (short) (OFFSET_CDATA + RSA_KEY_BYTESIZE);


        //TODO response
        // Response (1 byte)
    }

    private byte[] personalizeStepThree (byte[] buffer, byte dataLength) {
        // Certificate (130 byte)
        for (short i = 0; i < CERTIFICATE_BYTESIZE; i++) {
            cardCertificate[i] = buffer[OFFSET_CDATA + i];
        }

        //TODO response
        // Response (1 byte)
    }

    /*private void respond() { I think this is javacard 2.2.2

    }*/

    /**
     * Called by the JavaCard OS when the applet is deselected. Can be overridden if we have to perform some cleanup.
     * Note that the deselect() of app A is called before the select() of app B.
     */
    /*public void deselect() {

    }*/

}
