import java.lang.reflect.InvocationTargetException;
import java.security.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class NaiveCoinTester {
	public int nPeople;
	public int nUTXOTx;
	public int maxUTXOTxOutput;
	public double maxValue;
	public int nTxPerTest;
	public int maxInput;
	public int maxOutput;

	public ArrayList<KeyPair> people;

	public NaiveCoinTester() {
		this.nPeople = 20;
		this.nUTXOTx = 20;
		this.maxUTXOTxOutput = 20;
		this.maxValue = 10;
		this.nTxPerTest = 50;
		this.maxInput = 4;
		this.maxOutput = 20;

		byte[] key = new byte[32];
		for (int i = 0; i < 32; i++) {
			key[i] = (byte) 1;
		}

		people = new ArrayList<KeyPair>();
		for (int i = 0; i < nPeople; i++) {
			KeyPairGenerator keyGen;
			try {
				keyGen = KeyPairGenerator.getInstance("RSA");
				keyGen.initialize(512);
				KeyPair pair = keyGen.genKeyPair();
				people.add(pair);
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	private byte[] signMessage(PrivateKey privateKey, byte[] message) {
		Signature sig;
		try {
			sig = Signature.getInstance("SHA256withRSA");
			sig.initSign(privateKey);
			sig.update(message);
			byte[] sigBytes = sig.sign();
			return sigBytes;
		} catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	private void printPassFail(int testNo, boolean passed) {
		String resStr = passed ? "PASSED" : "FAILED";
		System.out.println("Test " + Integer.toString(testNo) + ": " + resStr);
	}

	public boolean test1() {
		System.out.println("Test 1: Process a block with no transactions");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.mine(2);

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		block.mine(2);
		boolean passes = blockHandler.processBlock(block);
		printPassFail(1, passes);
		return passes;
	}

	public boolean test2() {
		System.out.println("Test 2: Process a block with a single valid transaction");

		Block genesisBlock = new Block(null, people.get(0).getPublic());
		genesisBlock.mine(2);

		BlockChain blockChain = new BlockChain(genesisBlock);
		BlockHandler blockHandler = new BlockHandler(blockChain);

		Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
		Transaction spendCoinbaseTx = new Transaction();
		spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
		spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublic());
		spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)), 0);
		spendCoinbaseTx.finalize();
		block.addTransaction(spendCoinbaseTx);
		block.mine(2);

		boolean passes = blockHandler.processBlock(block);
		printPassFail(2, passes);
		return passes;
	}

	public boolean test3() {
		System.out.println("Test 3: Process a block with many valid transactions");
		boolean passes = true;

		for (int k = 0; k < 20; k++) {
			Block genesisBlock = new Block(null, people.get(0).getPublic());
			genesisBlock.mine(2);

			BlockChain blockChain = new BlockChain(genesisBlock);
			BlockHandler blockHandler = new BlockHandler(blockChain);

			Block block = new Block(genesisBlock.getHash(), people.get(1).getPublic());
			Transaction spendCoinbaseTx = new Transaction();
			spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);

			double totalValue = 0;
			UTXOPool utxoPool = new UTXOPool();
			int numOutputs = 0;
			HashMap<UTXO, KeyPair> utxoToKeyPair = new HashMap<UTXO, KeyPair>();
			HashMap<Integer, KeyPair> keyPairAtIndex = new HashMap<Integer, KeyPair>();

			for (int j = 0; j < maxUTXOTxOutput; j++) {
				Random ran = new Random();
				int rIndex = ran.nextInt(people.size());// SampleRandom.randomInt(people.size());
				PublicKey addr = people.get(rIndex).getPublic();

				double value = ran.nextDouble() * maxValue;
				if (totalValue + value > Block.COINBASE)
					break;
				spendCoinbaseTx.addOutput(value, addr);
				keyPairAtIndex.put(j, people.get(rIndex));
				totalValue += value;
				numOutputs++;
			}

			spendCoinbaseTx.addSignature(signMessage(people.get(0).getPrivate(), spendCoinbaseTx.getRawDataToSign(0)),
					0);
			spendCoinbaseTx.finalize();
			block.addTransaction(spendCoinbaseTx);

			for (int j = 0; j < numOutputs; j++) {
				UTXO ut = new UTXO(spendCoinbaseTx.getHash(), j);
				utxoPool.addUTXO(ut, spendCoinbaseTx.getOutput(j));
				utxoToKeyPair.put(ut, keyPairAtIndex.get(j));
			}

			ArrayList<UTXO> utxoSet = utxoPool.getAllUTXO();
			HashSet<UTXO> utxosSeen = new HashSet<UTXO>();
			int maxValidInput = Math.min(maxInput, utxoSet.size());

			for (int i = 0; i < nTxPerTest; i++) {
				Transaction tx = new Transaction();
				HashMap<Integer, UTXO> utxoAtIndex = new HashMap<Integer, UTXO>();
				Random ran = new Random();
				int nInput = ran.nextInt(maxValidInput) + 1;
				int numInputs = 0;
				double inputValue = 0;
				for (int j = 0; j < nInput; j++) {
					UTXO utxo = utxoSet.get(ran.nextInt(utxoSet.size()));
					if (!utxosSeen.add(utxo)) {
						j--;
						nInput--;
						continue;
					}
					tx.addInput(utxo.getTxHash(), utxo.getIndex());
					inputValue += utxoPool.getTxOutput(utxo).value;
					utxoAtIndex.put(j, utxo);
					numInputs++;
				}

				if (numInputs == 0)
					continue;

				int nOutput = ran.nextInt(maxOutput) + 1;
				double outputValue = 0;
				for (int j = 0; j < nOutput; j++) {
					double value = ran.nextDouble() * maxValue;
					if (outputValue + value > inputValue)
						break;
					int rIndex = ran.nextInt(people.size());
					PublicKey addr = people.get(rIndex).getPublic();
					tx.addOutput(value, addr);
					outputValue += value;
				}
				for (int j = 0; j < numInputs; j++) {
					byte[] sig = signMessage(utxoToKeyPair.get(utxoAtIndex.get(j)).getPrivate(),
							tx.getRawDataToSign(j));
					tx.addSignature(sig, j);
				}
				tx.finalize();
				block.addTransaction(tx);
			}

			block.mine(2);

			passes = passes && blockHandler.processBlock(block);
		}
		printPassFail(3, passes);
		return passes;
	}

	public static void main(String[] args) {
		NaiveCoinTester runner = new NaiveCoinTester();

		int numSuccess = 0;
		int numTotal = 3;
		for(int i = 1; i <= numTotal; i++) {
			try {
				boolean success = (boolean)runner.getClass().getMethod("test" + Integer.toString(i)).invoke(runner);
				if(success) {
					numSuccess += 1;
				}
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
			}
		}	
		System.out.println("=============================================");
		System.out.println(Integer.toString(numSuccess) + "/" + Integer.toString(numTotal) + " tests passed");		

	}

}
