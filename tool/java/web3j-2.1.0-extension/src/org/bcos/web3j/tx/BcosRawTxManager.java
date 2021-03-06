/**
 * 
 */
package org.bcos.web3j.tx;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.RawTransaction;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.ChainId;
import org.web3j.tx.TransactionManager;
import org.web3j.utils.Numeric;

/**
 * @author Administrator
 *
 */
public class BcosRawTxManager extends TransactionManager {

	private final Web3j web3j;
    final Credentials credentials;

    private final byte chainId;
    private BcosTxEncoder transactionEncoder;
    private Random random; 

    public BcosRawTxManager(Web3j web3j, Credentials credentials, byte chainId) {
        super(web3j);
        this.web3j = web3j;
        this.credentials = credentials;

        this.chainId = chainId;
        
        random = new Random();
        random.setSeed(Calendar.getInstance().getTimeInMillis());
        
        this.transactionEncoder = new BcosTxEncoder();
    }

    public BcosRawTxManager(
            Web3j web3j, Credentials credentials, byte chainId, int attempts, int sleepDuration) {
        super(web3j, attempts, sleepDuration);
        this.web3j = web3j;
        this.credentials = credentials;

        this.chainId = chainId;
        
        random = new Random();
        random.setSeed(Calendar.getInstance().getTimeInMillis());
        this.transactionEncoder = new BcosTxEncoder();
    }

    public BcosRawTxManager(Web3j web3j, Credentials credentials) {
        this(web3j, credentials, ChainId.NONE);
    }

    public BcosRawTxManager(
            Web3j web3j, Credentials credentials, int attempts, int sleepDuration) {
        this(web3j, credentials, ChainId.NONE, attempts, sleepDuration);
    }

    BigInteger getNonce() throws ExecutionException, InterruptedException {
        EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();

        return ethGetTransactionCount.getTransactionCount();
    }

    @Override
    public EthSendTransaction sendTransaction(
            BigInteger gasPrice, BigInteger gasLimit, String to,
            String data, BigInteger value) throws ExecutionException, InterruptedException {

        BigInteger nonce = getNonce();

        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                to,
                value,
                data);

        return signAndSend(rawTransaction);
    }

    public EthSendTransaction signAndSend(RawTransaction rawTransaction)
            throws ExecutionException, InterruptedException {

        byte[] signedMessage;

        //retrieve the blockNumber
        org.web3j.protocol.core.Request<?,EthBlockNumber> blockNumber = web3j.ethBlockNumber();
        EthBlockNumber blockNumber2 = new EthBlockNumber();
        BigInteger blockLimited = null;
        try {
			blockNumber2 = blockNumber.send();
			blockLimited = blockNumber2.getBlockNumber();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        blockLimited = blockLimited.add(new BigInteger("100", 10));
        transactionEncoder.setBlockLimited(blockLimited);
        
        //generate ramdomid
        BigInteger ramdomid = new BigInteger(256, random);
        transactionEncoder.setRandomId(ramdomid);
        
        if (chainId > ChainId.NONE) {
            signedMessage = transactionEncoder.signMessage(rawTransaction, chainId, credentials);
        } else {
            signedMessage = transactionEncoder.signMessage(rawTransaction, credentials);
        }

        String hexValue = Numeric.toHexString(signedMessage);

        return web3j.ethSendRawTransaction(hexValue).sendAsync().get();
    }

    @Override
    public String getFromAddress() {
        return credentials.getAddress();
    }

}
