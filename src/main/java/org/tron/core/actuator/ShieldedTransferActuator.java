package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sun.jna.Pointer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.tron.common.utils.ByteArray;
import org.tron.common.zksnark.Librustzcash;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.BytesCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.db.Manager;
import org.tron.core.exception.BalanceInsufficientException;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.exception.ZksnarkException;
import org.tron.core.zen.merkle.IncrementalMerkleTreeContainer;
import org.tron.core.zen.merkle.MerkleContainer;
import org.tron.protos.Contract.ReceiveDescription;
import org.tron.protos.Contract.ShieldedTransferContract;
import org.tron.protos.Contract.SpendDescription;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;


@Slf4j(topic = "actuator")
public class ShieldedTransferActuator extends AbstractActuator {

  private TransactionCapsule tx;
  private ShieldedTransferContract shieldedTransferContract;

  ShieldedTransferActuator(Any contract, Manager dbManager, TransactionCapsule tx) {
    super(contract, dbManager);
    this.tx = tx;
    try {
      shieldedTransferContract = contract.unpack(ShieldedTransferContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  @Override
  public boolean execute(TransactionResultCapsule ret)
      throws ContractExeException {

    long fee = calcFee();
    try {
      if (shieldedTransferContract.getTransparentFromAddress().toByteArray().length > 0) {
        executeTransparentFrom(shieldedTransferContract.getTransparentFromAddress().toByteArray(),
            shieldedTransferContract.getFromAmount());
      }
      dbManager.adjustBalance(dbManager.getAccountStore().getBlackhole().createDbKey(), fee);
    } catch (BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    executeShielded(shieldedTransferContract.getSpendDescriptionList(),
        shieldedTransferContract.getReceiveDescriptionList());

    if (shieldedTransferContract.getTransparentToAddress().toByteArray().length > 0) {
      executeTransparentTo(shieldedTransferContract.getTransparentToAddress().toByteArray(),
          shieldedTransferContract.getToAmount());
    }
    ret.setStatus(fee, code.SUCESS);
    long totalShieldedPoolValue = dbManager.getDynamicPropertiesStore().getTotalShieldedPoolValue();
    long valueBalance = shieldedTransferContract.getToAmount() -
        shieldedTransferContract.getFromAmount() + fee;
    totalShieldedPoolValue -= valueBalance;
    dbManager.getDynamicPropertiesStore().saveTotalShieldedPoolValue(totalShieldedPoolValue);
    return true;
  }

  private void executeTransparentFrom(byte[] ownerAddress, long amount)
      throws ContractExeException {
    try {
      dbManager.adjustBalance(ownerAddress, -amount);
    } catch (BalanceInsufficientException e) {
      throw new ContractExeException(e.getMessage());
    }
  }

  private void executeTransparentTo(byte[] toAddress, long amount) throws ContractExeException {
    try {
      AccountCapsule toAccount = dbManager.getAccountStore().get(toAddress);
      if (toAccount == null) {
        boolean withDefaultPermission =
            dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        toAccount = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal,
            dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddress, toAccount);
      }
      dbManager.adjustBalance(toAddress, amount);
    } catch (BalanceInsufficientException e) {
      throw new ContractExeException(e.getMessage());
    }
  }

  //record shielded transaction data.
  private void executeShielded(List<SpendDescription> spends, List<ReceiveDescription> receives)
   throws ContractExeException{
    //handle spends
    for (SpendDescription spend : spends) {
      if (dbManager.getNullfierStore().has(
          new BytesCapsule(spend.getNullifier().toByteArray()).getData())) {
        throw new ContractExeException("double spend");
      }
      dbManager.getNullfierStore().put(new BytesCapsule(spend.getNullifier().toByteArray()));
    }

    MerkleContainer merkleContainer = dbManager.getMerkleContainer();
    IncrementalMerkleTreeContainer currentMerkle = merkleContainer.getCurrentMerkle();

    //handle receives
    for (ReceiveDescription receive : receives) {
      merkleContainer
          .saveCmIntoMerkleTree(currentMerkle, receive.getNoteCommitment().toByteArray());
    }

    try{
      currentMerkle.wfcheck();
    }catch (ZksnarkException e){
      throw new ContractExeException(e.getMessage());
    }

    merkleContainer.setCurrentMerkle(currentMerkle);
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(ShieldedTransferContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [ShieldedTransferContract],real type[" + contract
              .getClass() + "]");
    }

    if (!dbManager.getDynamicPropertiesStore().supportZKSnarkTransaction()) {
      throw new ContractValidateException("Not support ZKSnarkTransaction, need to be opened by" +
          " the committee");
    }

    byte[] signHash;
    try {
      signHash = TransactionCapsule.hash(tx);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    if (!checkSender(shieldedTransferContract)) {
      return false;
    }
    if (!checkReceiver(shieldedTransferContract)) {
      return false;
    }

    long fee = calcFee();
    if (shieldedTransferContract.getFee() != fee) {
      throw new ContractValidateException("ShieldedTransferContract fee must equal " + fee);
    }

    //transparent verification
    if (!validateTransparent(shieldedTransferContract)) {
      return false;
    }

    List<SpendDescription> spendDescriptions = shieldedTransferContract.getSpendDescriptionList();
    // check duplicate sapling nullifiers
    if (CollectionUtils.isNotEmpty(spendDescriptions)) {
      HashSet<ByteString> nfSet = new HashSet<>();
      for (SpendDescription spendDescription : spendDescriptions) {
        if (nfSet.contains(spendDescription.getNullifier())) {
          throw new ContractValidateException("duplicate sapling nullifiers in this transaction");
        }
        nfSet.add(spendDescription.getNullifier());
        if (!dbManager.getMerkleContainer()
            .merkleRootExist(spendDescription.getAnchor().toByteArray())) {
          throw new ContractValidateException("Rt is invalid.");
        }
        if (dbManager.getNullfierStore().has(spendDescription.getNullifier().toByteArray())) {
          throw new ContractValidateException("note has been spend in this transaction");
        }
      }
    }

    List<ReceiveDescription> receiveDescriptions = shieldedTransferContract
        .getReceiveDescriptionList();

    HashSet<ByteString> receiveSet = new HashSet<>();
    for (ReceiveDescription receiveDescription : receiveDescriptions) {
      if (receiveSet.contains(receiveDescription.getNoteCommitment())) {
        throw new ContractValidateException("duplicate cm in receive_description");
      }
      receiveSet.add(receiveDescription.getNoteCommitment());
    }
    if (CollectionUtils.isEmpty(spendDescriptions)
        && CollectionUtils.isEmpty(receiveDescriptions)) {
      throw new ContractValidateException("no Description found in transaction");
    }

    if (CollectionUtils.isNotEmpty(spendDescriptions)
        || CollectionUtils.isNotEmpty(receiveDescriptions)) {
      Pointer ctx = Librustzcash.librustzcashSaplingVerificationCtxInit();
      for (SpendDescription spendDescription : spendDescriptions) {
        if (spendDescription.getValueCommitment().isEmpty()
            || spendDescription.getAnchor().isEmpty()
            || spendDescription.getNullifier().isEmpty()
            || spendDescription.getZkproof().isEmpty()
            || spendDescription.getSpendAuthoritySignature().isEmpty()) {
          Librustzcash.librustzcashSaplingVerificationCtxFree(ctx);
          throw new ContractValidateException("spend description null");
        }
        if (!Librustzcash.librustzcashSaplingCheckSpend(
            ctx,
            spendDescription.getValueCommitment().toByteArray(),
            spendDescription.getAnchor().toByteArray(),
            spendDescription.getNullifier().toByteArray(),
            spendDescription.getRk().toByteArray(),
            spendDescription.getZkproof().toByteArray(),
            spendDescription.getSpendAuthoritySignature().toByteArray(),
            signHash
        )) {
          Librustzcash.librustzcashSaplingVerificationCtxFree(ctx);
          throw new ContractValidateException("librustzcashSaplingCheckSpend error");
        }
      }

      for (ReceiveDescription receiveDescription : receiveDescriptions) {
        if (receiveDescription.getValueCommitment().isEmpty()
            || receiveDescription.getNoteCommitment().isEmpty()
            || receiveDescription.getEpk().isEmpty()
            || receiveDescription.getZkproof().isEmpty()
            || receiveDescription.getCEnc().isEmpty()
            || receiveDescription.getCOut().isEmpty()) {
          Librustzcash.librustzcashSaplingVerificationCtxFree(ctx);
          throw new ContractValidateException("receive description null");
        }
        if (!Librustzcash.librustzcashSaplingCheckOutput(
            ctx,
            receiveDescription.getValueCommitment().toByteArray(),
            receiveDescription.getNoteCommitment().toByteArray(),
            receiveDescription.getEpk().toByteArray(),
            receiveDescription.getZkproof().toByteArray()
        )) {
          Librustzcash.librustzcashSaplingVerificationCtxFree(ctx);
          throw new ContractValidateException("librustzcashSaplingCheckOutput error");
        }
      }

      long valueBalance = shieldedTransferContract.getToAmount() -
          shieldedTransferContract.getFromAmount() + fee;
      long totalShieldedPoolValue = dbManager.getDynamicPropertiesStore()
          .getTotalShieldedPoolValue();
      totalShieldedPoolValue -= valueBalance;
      if (totalShieldedPoolValue < 0) {
        throw new ContractValidateException("shieldedPoolValue error");
      }
      if (!Librustzcash.librustzcashSaplingFinalCheck(
          ctx,
          valueBalance,
          shieldedTransferContract.getBindingSignature().toByteArray(),
          signHash
      )) {
        Librustzcash.librustzcashSaplingVerificationCtxFree(ctx);
        throw new ContractValidateException("librustzcashSaplingFinalCheck error");
      }
    }
    return true;
  }

  private boolean checkSender(ShieldedTransferContract shieldedTransferContract)
      throws ContractValidateException {
    if (shieldedTransferContract.getTransparentFromAddress().toByteArray().length > 0
        && shieldedTransferContract.getSpendDescriptionCount() > 0) {
      throw new ContractValidateException("ShieldedTransferContract error, more than 1 senders");
    }
    if (shieldedTransferContract.getTransparentFromAddress().toByteArray().length == 0
        && shieldedTransferContract.getSpendDescriptionCount() == 0) {
      throw new ContractValidateException("ShieldedTransferContract error, no sender");
    }
    if (shieldedTransferContract.getSpendDescriptionCount() > 10) {
      throw new ContractValidateException("ShieldedTransferContract error, number of spend notes"
          + " should not be more than 10");
    }
    return true;
  }

  private boolean checkReceiver(ShieldedTransferContract shieldedTransferContract)
      throws ContractValidateException {
    if (shieldedTransferContract.getTransparentToAddress().toByteArray().length == 0
        && shieldedTransferContract.getReceiveDescriptionCount() == 0) {
      throw new ContractValidateException("ShieldedTransferContract error, no receiver");
    }
    if (shieldedTransferContract.getReceiveDescriptionCount() > 10) {
      throw new ContractValidateException("ShieldedTransferContract error, number of receivers"
          + " should not be more than 10");
    }
    return true;
  }

  private boolean validateTransparent(ShieldedTransferContract shieldedTransferContract)
      throws ContractValidateException {
    boolean hasTransparentFrom;
    boolean hasTransparentTo;
    byte[] toAddress = shieldedTransferContract.getTransparentToAddress().toByteArray();
    byte[] ownerAddress = shieldedTransferContract.getTransparentFromAddress().toByteArray();

    hasTransparentFrom = (ownerAddress.length > 0);
    hasTransparentTo = (toAddress.length > 0);

    long fromAmount = shieldedTransferContract.getFromAmount();
    long toAmount = shieldedTransferContract.getToAmount();

    if (hasTransparentFrom && !Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid transparent_from_address");
    }
    if (!hasTransparentFrom && fromAmount != 0) {
      throw new ContractValidateException("no transparent_from_address, from_amount should be 0");
    }
    if (hasTransparentTo && !Wallet.addressValid(toAddress)) {
      throw new ContractValidateException("Invalid transparent_to_address");
    }
    if (!hasTransparentTo && toAmount != 0) {
      throw new ContractValidateException("no transparent_to_address, to_amount should be 0");
    }
    if (hasTransparentFrom && hasTransparentTo && Arrays.equals(toAddress, ownerAddress)) {
      throw new ContractValidateException("Can't transfer trx to yourself");
    }

    if (hasTransparentFrom) {
      AccountCapsule ownerAccount = dbManager.getAccountStore().get(ownerAddress);
      if (ownerAccount == null) {
        throw new ContractValidateException("Validate ShieldedTransferContract error, "
            + "no OwnerAccount");
      }

      long balance = ownerAccount.getBalance();

      if (fromAmount <= 0) {
        throw new ContractValidateException("from_amount must be greater than 0");
      }

      if (balance < fromAmount) {
        throw new ContractValidateException(
            "Validate ShieldedTransferContract error, balance is not sufficient");
      }
    }

    if (hasTransparentTo) {
      if (toAmount <= 0) {
        throw new ContractValidateException("to_amount must be greater than 0");
      }
      AccountCapsule toAccount = dbManager.getAccountStore().get(toAddress);
      if (toAccount != null) {
        try {
          Math.addExact(toAccount.getBalance(), toAmount);
        } catch (ArithmeticException e) {
          logger.debug(e.getMessage(), e);
          throw new ContractValidateException(e.getMessage());
        }
      }
    }
    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return null;
  }

  @Override
  public long calcFee() {
    long fee = 0;
//    byte[] toAddress = shieldedTransferContract.getTransparentToAddress().toByteArray();
//    if (Wallet.addressValid(toAddress)) {
//      AccountCapsule transparentToAccount = dbManager.getAccountStore().get(toAddress);
//      if (transparentToAccount == null) {
//        fee = dbManager.getDynamicPropertiesStore().getCreateAccountFee();
//      }
//    }
    fee += dbManager.getDynamicPropertiesStore().getShieldedTransactionFee();
    return fee;
  }
}