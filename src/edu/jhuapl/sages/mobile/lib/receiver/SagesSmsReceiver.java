package edu.jhuapl.sages.mobile.lib.receiver;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;

import javax.crypto.NoSuchPaddingException;

import org.spongycastle.util.encoders.Base64;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.widget.Toast;
import edu.jhuapl.sages.mobile.lib.SagesConstants;
//import edu.jhuapl.sages.mobile.lib.app.crypto.ShowDecryptedMessageActivity;
//import edu.jhuapl.sages.mobile.lib.app.tests.crypto.SagesKeyTest;
import edu.jhuapl.sages.mobile.lib.crypto.engines.CryptoEngine;
import edu.jhuapl.sages.mobile.lib.crypto.persisted.SagesKey.KeyEnum;
import edu.jhuapl.sages.mobile.lib.crypto.persisted.SagesKeyException;
import edu.jhuapl.sages.mobile.lib.crypto.persisted.SagesKeyStore;
import edu.jhuapl.sages.mobile.lib.crypto.persisted.SagesPrivateKey;
import edu.jhuapl.sages.mobile.lib.crypto.persisted.SagesPublicKey;
import edu.jhuapl.sages.mobile.lib.message.DataMessage;
import edu.jhuapl.sages.mobile.lib.message.KeyExchangeMessage;
import edu.jhuapl.sages.mobile.lib.message.MessageBuilderUtil;
import edu.jhuapl.sages.mobile.lib.message.SagesMessage;
import edu.jhuapl.sages.mobile.lib.message.SagesMessage.MsgTypeEnum;

/**
 * TODO:
 * - if exception occurs during key-exch, need to send back NACK
 * - need to keep track of state of exchange model to ensure all steps occurred and in sync both sides
 * 
 * @author pokuam1
 * @date May, 5 2013
 */
public class SagesSmsReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle extras = intent.getExtras();
		
		if (extras == null) {
			return;
		}

		Toast.makeText(context, "SMS Received", Toast.LENGTH_SHORT).show();
		
		Object[] pdus = (Object[])extras.get("pdus");
		for (int i = 0; i < pdus.length; i++){
			SmsMessage sms = SmsMessage.createFromPdu((byte[])pdus[i]);
			String sender = sms.getOriginatingAddress();
			String body = sms.getMessageBody();
			
			/**
			 * if sms body contains the text for "key_exch", then process as key exchange message.
			 *  -> extracts the public key
			 *  -> saves public key for this sender to the keystore
			 *  
			 * if sms body contains the text for "data", and also "enc aes" then process as an encrypted msg
			 */
			boolean keyExchangeSuccess = false;
			try {
				if (body.contains(SagesMessage.data)){
					DataMessage dataMsg = new DataMessage();
					dataMsg.setSender(sender);
					dataMsg.processRawMessage(body);
					
					if (body.contains("enc aes")){
						DataMessage dataMessage = new DataMessage();
						dataMessage.setSender(sender);
						dataMessage.processRawMessage(body);
						
						//TODO: when production key store should live on internal device storage. during testing allows to have visibility.
						File file = SagesConstants.KEYSTORE_FILE;
						SagesKeyStore keystore = new SagesKeyStore(file);
						
						SagesPrivateKey dummy_private_aes = (SagesPrivateKey) keystore.lookupKey("+12404759206", KeyEnum.PRIVATE);
						try {
							CryptoEngine crytpoengine = new CryptoEngine(dummy_private_aes.getData());
							String b64Encoded = dataMsg.getBody().getBody();

							byte[] b64EncodedCipher = (dataMsg.getBody().getBody().getBytes());
							byte[] b64DecodedCipher = Base64.decode(b64EncodedCipher);
							byte[] decryptedMsg = crytpoengine.decrypt(b64DecodedCipher);
							String raw = new String(decryptedMsg);
							
							//TODO patch for real use
//							Intent intentDecrypt = new Intent(context, ShowDecryptedMessageActivity.class);
//							intentDecrypt.putExtra("msg_body", body);
//							intentDecrypt.putExtra("msg_body_decrypted", decryptedMsg);
//							intentDecrypt.putExtra("msg_sender", sender);
//							intentDecrypt.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//							context.startActivity(intentDecrypt);
							break;

						} catch (NoSuchAlgorithmException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (NoSuchPaddingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				else if (body.contains(SagesMessage.key_exch)){
					//TODO: when production key store should live on internal device storage. during testing allows to have visibility.
					File file = SagesConstants.KEYSTORE_FILE;
					SagesKeyStore keystore = new SagesKeyStore(file);

					
					if(body.contains(SagesMessage.init)){ 
						Toast.makeText(context, "Key exchange initiated by " + sender, Toast.LENGTH_SHORT).show();
						/* Just recieved a KeyExchange[init] from sender, so I will need:
						 * 1) extract sender's key and save key to my keystore
						 * 2) return my own keys back to the sender by sending a KeyExchange[reply] as SMS
						 *    2a) also, if i don't have my keys stored, save them (intend to only encounter this in testing)
						 */
						
						if (body.contains("aes")){ //TODO this is for quick test only
							KeyExchangeMessage keyExch = new KeyExchangeMessage();
							keyExch.setSender(sender);
							keyExch.processRawMessage(body);
							SagesPrivateKey privKey = (SagesPrivateKey) keyExch.getKey();
							
							keystore.addKey(privKey);
							//TODO patch for real use
//							Intent intentDecrypt = new Intent(context, ShowDecryptedMessageActivity.class);
//							intentDecrypt.putExtra("msg_body", body);
//							intentDecrypt.putExtra("msg_sender", sender);
//							intentDecrypt.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//							context.startActivity(intentDecrypt);
							break;
						} else {
						//1)
						KeyExchangeMessage keyExch = new KeyExchangeMessage();
						keyExch.setSender(sender);
						keyExch.processRawMessage(body);
						SagesPublicKey pubKey = (SagesPublicKey) keyExch.getKey();
						
						keystore.addKey(pubKey);
						}
						//2)
						
						String msgHeader = MessageBuilderUtil.genTestHeader(MsgTypeEnum.KEY_EXCH, "reply");
						//TODO patch for real use
						String msgBody = SagesMessage.my_key ;//+ SagesKeyTest.testData + "- for key:" + MainActivity.NUMBER_THIS_DEVICE + " - " + Calendar.MILLISECOND;
//						String msgBody = SagesMessage.my_key + SagesKeyTest.testData + "- for key:" + MainActivity.NUMBER_THIS_DEVICE + " - " + Calendar.MILLISECOND;
//						try {
//							MainActivity.saveOwnKeyPriorToSMS(context, "+" + MainActivity.NUMBER_THIS_DEVICE, (msgHeader + msgBody));
//						} catch (SagesKeyException e1){
//							Toast.makeText(context, "Unable to save own key. Key exchange reply will not be sent to " + sender, Toast.LENGTH_SHORT).show();
//						}
						
						SmsManager smsManager = SmsManager.getDefault();
						smsManager.sendTextMessage(sender, null, msgHeader + msgBody, null, null);
						
					} else if (body.contains(SagesMessage.reply)){
						Toast.makeText(context, "Key exchange reply from " + sender, Toast.LENGTH_SHORT).show();
						/* This has the remote key from the device I requested key for, and it needs to be saved to my keystore 
						 * 1) save key for the remote sender
						 * 
						 */
						KeyExchangeMessage keyExch = new KeyExchangeMessage();
						keyExch.setSender(sender);
						keyExch.processRawMessage(body);
						SagesPublicKey pubKey = (SagesPublicKey) keyExch.getKey();
						
						keystore.addKey(pubKey);
					}
					
					Toast.makeText(context, "Public key stored for sender " + sender, Toast.LENGTH_SHORT).show();
				}
				
				/**
				 * Now time to respond by sending own(aka 'remote' to remove conufsion) public key back to the initiator
				 * TOOD: break this into another process?
				 */
				
			} catch (SagesKeyException e) {
				e.printStackTrace();
				// SEND NACK ON KEY EXCHANGE
					// TODO
			}
			}
		
			
		}
	}