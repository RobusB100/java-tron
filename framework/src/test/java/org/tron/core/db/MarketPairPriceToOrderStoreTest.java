package org.tron.core.db;

import java.io.File;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.ByteUtil;
import org.tron.common.utils.FileUtil;
import org.tron.core.ChainBaseManager;
import org.tron.core.Constant;
import org.tron.core.capsule.MarketOrderIdListCapsule;
import org.tron.core.capsule.utils.MarketUtils;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.core.store.MarketPairPriceToOrderStore;
import org.tron.core.store.MarketPairToPriceStore;
import org.tron.protos.Protocol.MarketPrice;

@Slf4j
public class MarketPairPriceToOrderStoreTest {

  private static final String dbPath = "output-MarketPairPriceToOrderStore-test";
  private static TronApplicationContext context;
  private static Manager dbManager;

  static {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    context = new TronApplicationContext(DefaultConfig.class);
  }

  // MarketPairPriceToOrderStore store;

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
  }

  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  // @Before
  // public void initDb() {
  //   // this.store = context.getBean(MarketPairPriceToOrderStore.class);
  //   ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
  //   this.store = chainBaseManager.getMarketPairPriceToOrderStore();
  // }

  @After
  public void cleanDb() {
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    MarketPairPriceToOrderStore marketPairPriceToOrderStore = chainBaseManager
        .getMarketPairPriceToOrderStore();
    MarketPairToPriceStore marketPairToPriceStore = dbManager.getChainBaseManager()
        .getMarketPairToPriceStore();

    marketPairPriceToOrderStore.forEach(
        entry -> marketPairPriceToOrderStore.delete(entry.getKey())
    );
    marketPairToPriceStore.forEach(
        entry -> marketPairToPriceStore.delete(entry.getKey())
    );
  }

  @Test
  public void testOrderWithSamePair() {
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    MarketPairPriceToOrderStore marketPairPriceToOrderStore = chainBaseManager
        .getMarketPairPriceToOrderStore();

    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("200");
    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2001L
    );
    byte[] pairPriceKey2 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2002L
    );
    byte[] pairPriceKey3 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2003L
    );

    MarketOrderIdListCapsule capsule1 = new MarketOrderIdListCapsule(ByteArray.fromLong(1),
        ByteArray.fromLong(1));
    MarketOrderIdListCapsule capsule2 = new MarketOrderIdListCapsule(ByteArray.fromLong(2),
        ByteArray.fromLong(2));
    MarketOrderIdListCapsule capsule3 = new MarketOrderIdListCapsule(ByteArray.fromLong(3),
        ByteArray.fromLong(3));

    //Use out-of-order insertion，key in store should be 1,2,3
    marketPairPriceToOrderStore.put(pairPriceKey2, capsule2);
    marketPairPriceToOrderStore.put(pairPriceKey1, capsule1);
    marketPairPriceToOrderStore.put(pairPriceKey3, capsule3);

    try {
      Assert
          .assertArrayEquals(capsule1.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey1).getData());
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey2).getData());
      Assert
          .assertArrayEquals(capsule3.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey3).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    byte[] nextKey = marketPairPriceToOrderStore.getNextKey(pairPriceKey2);

    Assert.assertArrayEquals("nextKey should be pairPriceKey3", pairPriceKey3, nextKey);
  }

  @Test
  public void testOrderWithSamePairOrdinal() {
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    MarketPairPriceToOrderStore marketPairPriceToOrderStore = chainBaseManager
        .getMarketPairPriceToOrderStore();

    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("200");
    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2001L
    );
    byte[] pairPriceKey2 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2002L
    );
    byte[] pairPriceKey3 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2003L
    );

    MarketOrderIdListCapsule capsule1 = new MarketOrderIdListCapsule(ByteArray.fromLong(1),
        ByteArray.fromLong(1));
    MarketOrderIdListCapsule capsule2 = new MarketOrderIdListCapsule(ByteArray.fromLong(2),
        ByteArray.fromLong(2));
    MarketOrderIdListCapsule capsule3 = new MarketOrderIdListCapsule(ByteArray.fromLong(3),
        ByteArray.fromLong(3));

    //Use out-of-order insertion，key in store should be 1,2,3
    marketPairPriceToOrderStore.put(pairPriceKey1, capsule1);
    marketPairPriceToOrderStore.put(pairPriceKey2, capsule2);
    marketPairPriceToOrderStore.put(pairPriceKey3, capsule3);

    try {
      Assert
          .assertArrayEquals(capsule1.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey1).getData());
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey2).getData());
      Assert
          .assertArrayEquals(capsule3.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey3).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    byte[] nextKey = marketPairPriceToOrderStore.getNextKey(pairPriceKey2);

    Assert.assertArrayEquals("nextKey should be pairPriceKey3", pairPriceKey3, nextKey);
  }

  @Test
  public void testAddPrice() {
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    MarketPairPriceToOrderStore marketPairPriceToOrderStore = chainBaseManager
        .getMarketPairPriceToOrderStore();

    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("200");
    byte[] pairPriceKey0 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        0L,
        0L
    );
    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        3L,
        3L
    );
    byte[] pairPriceKey2 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1L,
        2L
    );
    byte[] pairPriceKey3 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1L,
        3L
    );

    MarketOrderIdListCapsule capsule0 = new MarketOrderIdListCapsule(ByteArray.fromLong(0),
        ByteArray.fromLong(0));
    MarketOrderIdListCapsule capsule1 = new MarketOrderIdListCapsule(ByteArray.fromLong(1),
        ByteArray.fromLong(1));
    MarketOrderIdListCapsule capsule2 = new MarketOrderIdListCapsule(ByteArray.fromLong(2),
        ByteArray.fromLong(2));
    MarketOrderIdListCapsule capsule3 = new MarketOrderIdListCapsule(ByteArray.fromLong(3),
        ByteArray.fromLong(3));

    //Use out-of-order insertion，key in store should be 1,2,3
    marketPairPriceToOrderStore.put(pairPriceKey2, capsule2);
    marketPairPriceToOrderStore.put(pairPriceKey1, capsule1);
    marketPairPriceToOrderStore.put(pairPriceKey3, capsule3);
    marketPairPriceToOrderStore.put(pairPriceKey0, capsule0);

    try {
      Assert
          .assertArrayEquals(capsule0.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey0).getData());
      Assert
          .assertArrayEquals(capsule1.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey1).getData());
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey2).getData());
      Assert
          .assertArrayEquals(capsule3.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey3).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    byte[] nextKey = marketPairPriceToOrderStore.getNextKey(pairPriceKey2);

    Assert.assertArrayEquals("nextKey should be pairPriceKey3", pairPriceKey3, nextKey);

    List<byte[]> keyList = marketPairPriceToOrderStore.getKeysNext(pairPriceKey0, 4);
    Assert.assertArrayEquals(pairPriceKey0, keyList.get(0));
    Assert.assertArrayEquals(pairPriceKey1, keyList.get(1));
    Assert.assertArrayEquals(pairPriceKey2, keyList.get(2));
    Assert.assertArrayEquals(pairPriceKey3, keyList.get(3));
  }

  @Test
  public void testAddPriceWithoutHeadKey() {
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    MarketPairPriceToOrderStore marketPairPriceToOrderStore = chainBaseManager
        .getMarketPairPriceToOrderStore();

    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("200");
    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        0L,
        0L
    );
    byte[] pairPriceKey2 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2002L
    );
    byte[] pairPriceKey3 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2003L
    );

    MarketOrderIdListCapsule capsule1 = new MarketOrderIdListCapsule(ByteArray.fromLong(1),
        ByteArray.fromLong(1));
    MarketOrderIdListCapsule capsule2 = new MarketOrderIdListCapsule(ByteArray.fromLong(2),
        ByteArray.fromLong(2));
    MarketOrderIdListCapsule capsule3 = new MarketOrderIdListCapsule(ByteArray.fromLong(3),
        ByteArray.fromLong(3));

    Assert.assertEquals(false, marketPairPriceToOrderStore.has(pairPriceKey1));
    Assert.assertEquals(false, marketPairPriceToOrderStore.has(pairPriceKey2));
    Assert.assertEquals(false, marketPairPriceToOrderStore.has(pairPriceKey3));

    //Use out-of-order insertion，key in store should be 1,2,3
    marketPairPriceToOrderStore.put(pairPriceKey2, capsule2);
    try {
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey2).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    Assert.assertEquals(true, marketPairPriceToOrderStore.has(pairPriceKey2));
    Assert.assertEquals(false, marketPairPriceToOrderStore.has(pairPriceKey1));
    Assert.assertEquals(false, marketPairPriceToOrderStore.has(pairPriceKey3));

    marketPairPriceToOrderStore.put(pairPriceKey3, capsule3);
    try {
      Assert
          .assertArrayEquals(capsule3.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey3).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    Assert.assertEquals(false, marketPairPriceToOrderStore.has(pairPriceKey1));
    Assert.assertEquals(true, marketPairPriceToOrderStore.has(pairPriceKey2));
    Assert.assertEquals(true, marketPairPriceToOrderStore.has(pairPriceKey3));

    // Assert
    //     .assertArrayEquals(capsule1.getData(), marketPairPriceToOrderStore.get(pairPriceKey1).getData());
    try {
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey2).getData());
      Assert
          .assertArrayEquals(capsule3.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey3).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    byte[] nextKey = marketPairPriceToOrderStore.getNextKey(pairPriceKey2);

    Assert.assertArrayEquals("nextKey should be pairPriceKey3", pairPriceKey3, nextKey);

  }

  @Test
  public void testAddPriceAndHeadKey() {
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    MarketPairPriceToOrderStore marketPairPriceToOrderStore = chainBaseManager
        .getMarketPairPriceToOrderStore();

    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("200");
    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        0L,
        0L
    );
    byte[] pairPriceKey2 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2002L
    );
    byte[] pairPriceKey3 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2003L
    );

    MarketOrderIdListCapsule capsule1 = new MarketOrderIdListCapsule(ByteArray.fromLong(1),
        ByteArray.fromLong(1));
    MarketOrderIdListCapsule capsule2 = new MarketOrderIdListCapsule(ByteArray.fromLong(2),
        ByteArray.fromLong(2));
    MarketOrderIdListCapsule capsule3 = new MarketOrderIdListCapsule(ByteArray.fromLong(3),
        ByteArray.fromLong(3));

    Assert.assertEquals(false, marketPairPriceToOrderStore.has(pairPriceKey1));
    Assert.assertEquals(false, marketPairPriceToOrderStore.has(pairPriceKey2));
    Assert.assertEquals(false, marketPairPriceToOrderStore.has(pairPriceKey3));

    //Use out-of-order insertion，key in store should be 1,2,3
    marketPairPriceToOrderStore.put(pairPriceKey1, capsule1);
    try {
      Assert
          .assertArrayEquals(capsule1.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey1).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    marketPairPriceToOrderStore.put(pairPriceKey2, capsule2);
    try {
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey2).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    marketPairPriceToOrderStore.put(pairPriceKey3, capsule3);
    try {
      Assert
          .assertArrayEquals(capsule3.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey3).getData());

      Assert
          .assertArrayEquals(capsule1.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey1).getData());
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey2).getData());
      Assert
          .assertArrayEquals(capsule3.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey3).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    byte[] nextKey = marketPairPriceToOrderStore.getNextKey(pairPriceKey2);

    Assert.assertArrayEquals("nextKey should be pairPriceKey3", pairPriceKey3, nextKey);
  }


  @Test
  public void testDecodePriceKey() {
    long sellTokenQuantity = 1000L;
    long buyTokenQuantity = 2001L;

    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("199");
    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        sellTokenQuantity,
        buyTokenQuantity
    );
    Assert.assertEquals(54, pairPriceKey1.length);

    MarketPrice marketPrice = MarketUtils.decodeKeyToMarketPrice(pairPriceKey1);
    Assert.assertEquals(sellTokenQuantity, marketPrice.getSellTokenQuantity());
    Assert.assertEquals(buyTokenQuantity, marketPrice.getBuyTokenQuantity());
  }

  @Test
  public void testPriceWithSamePair() {
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    MarketPairPriceToOrderStore marketPairPriceToOrderStore = chainBaseManager
        .getMarketPairPriceToOrderStore();
    MarketPairToPriceStore marketPairToPriceStore = chainBaseManager.getMarketPairToPriceStore();

    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("200");

    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2001L
    );
    byte[] pairPriceKey2 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2002L
    );
    byte[] pairPriceKey3 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1000L,
        2003L
    );

    Assert.assertEquals(0, marketPairToPriceStore.getPriceNum(sellTokenID1, buyTokenID1));

    //Use out-of-order insertion，key in store should be 1,2,3
    marketPairPriceToOrderStore.put(pairPriceKey1, new MarketOrderIdListCapsule());
    marketPairToPriceStore.addNewPriceKey(sellTokenID1, buyTokenID1, marketPairPriceToOrderStore);
    Assert.assertEquals(1, marketPairToPriceStore.getPriceNum(sellTokenID1, buyTokenID1));

    marketPairPriceToOrderStore.put(pairPriceKey2, new MarketOrderIdListCapsule());
    marketPairToPriceStore.addNewPriceKey(sellTokenID1, buyTokenID1, marketPairPriceToOrderStore);
    Assert.assertEquals(2, marketPairToPriceStore.getPriceNum(sellTokenID1, buyTokenID1));

    marketPairPriceToOrderStore.put(pairPriceKey3, new MarketOrderIdListCapsule());
    marketPairToPriceStore.addNewPriceKey(sellTokenID1, buyTokenID1, marketPairPriceToOrderStore);
    Assert.assertEquals(3, marketPairToPriceStore.getPriceNum(sellTokenID1, buyTokenID1));

    byte[] nextKey = marketPairPriceToOrderStore.getNextKey(pairPriceKey2);

    Assert.assertArrayEquals("nextKey should be pairPriceKey3", pairPriceKey3, nextKey);
  }

  @Test
  public void testPriceSeqWithSamePair() {
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    MarketPairPriceToOrderStore marketPairPriceToOrderStore = chainBaseManager
        .getMarketPairPriceToOrderStore();
    MarketPairToPriceStore marketPairToPriceStore = chainBaseManager.getMarketPairToPriceStore();

    // put order: pairPriceKey2 pairPriceKey1 pairPriceKey3 pairPriceKey0
    // lexicographical order: pairPriceKey0 < pairPriceKey3 < pairPriceKey1 < pairPriceKey2
    // key order: pairPriceKey0 < pairPriceKey1 = pairPriceKey2 < pairPriceKey3
    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("200");
    byte[] pairPriceKey0 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        0L,
        0L
    );
    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        2L,
        6L
    );
    byte[] pairPriceKey2 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        3L,
        9L
    );
    byte[] pairPriceKey3 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1L,
        4L
    );

    // lexicographical order: pairPriceKey0 < pairPriceKey3 < pairPriceKey1 < pairPriceKey2
    Assert.assertTrue(ByteUtil.compare(pairPriceKey0, pairPriceKey3) < 0);
    Assert.assertTrue(ByteUtil.compare(pairPriceKey3, pairPriceKey1) < 0);
    Assert.assertTrue(ByteUtil.compare(pairPriceKey1, pairPriceKey2) < 0);

    MarketOrderIdListCapsule capsule0 = new MarketOrderIdListCapsule(ByteArray.fromLong(0),
        ByteArray.fromLong(0));
    MarketOrderIdListCapsule capsule1 = new MarketOrderIdListCapsule(ByteArray.fromLong(1),
        ByteArray.fromLong(1));
    MarketOrderIdListCapsule capsule2 = new MarketOrderIdListCapsule(ByteArray.fromLong(2),
        ByteArray.fromLong(2));
    MarketOrderIdListCapsule capsule3 = new MarketOrderIdListCapsule(ByteArray.fromLong(3),
        ByteArray.fromLong(3));

    Assert.assertFalse(marketPairPriceToOrderStore.has(pairPriceKey2));
    if (!marketPairPriceToOrderStore.has(pairPriceKey2)) {
      marketPairPriceToOrderStore.put(pairPriceKey2, capsule2);
    }

    try {
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey2).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    // pairPriceKey1 and pairPriceKey2 has the same value, so pairPriceKey1 will not put
    Assert.assertTrue(marketPairPriceToOrderStore.has(pairPriceKey1));
    if (!marketPairPriceToOrderStore.has(pairPriceKey1)) {
      marketPairPriceToOrderStore.put(pairPriceKey1, capsule1);
      // this should not be executed
      Assert.assertTrue(true);
    }

    try {
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey1).getData());
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey2).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    Assert.assertFalse(marketPairPriceToOrderStore.has(pairPriceKey0));
    if (!marketPairPriceToOrderStore.has(pairPriceKey0)) {
      marketPairPriceToOrderStore.put(pairPriceKey0, capsule0);
    }

    Assert.assertFalse(marketPairPriceToOrderStore.has(pairPriceKey3));
    if (!marketPairPriceToOrderStore.has(pairPriceKey3)) {
      marketPairPriceToOrderStore.put(pairPriceKey3, capsule3);
    }

    // get pairPriceKey1, will get pairPriceKey2's value capsule2
    try {
      Assert
          .assertArrayEquals(capsule0.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey0).getData());
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey1).getData());
      Assert
          .assertArrayEquals(capsule2.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey2).getData());
      Assert
          .assertArrayEquals(capsule3.getData(),
              marketPairPriceToOrderStore.get(pairPriceKey3).getData());
    } catch (ItemNotFoundException e) {
      Assert.assertTrue(false);
    }

    byte[] nextKey = marketPairPriceToOrderStore.getNextKey(pairPriceKey2);
    Assert.assertArrayEquals("nextKey should be pairPriceKey3", pairPriceKey3, nextKey);

    // Assert.assertEquals(3, marketPairToPriceStore.getPriceNum(sellTokenID1, buyTokenID1));
    List<byte[]> keyList = marketPairPriceToOrderStore.getKeysNext(pairPriceKey0, 3 + 1);
    Assert.assertArrayEquals(pairPriceKey0, keyList.get(0));
    Assert.assertArrayEquals(pairPriceKey2, keyList.get(1));
    Assert.assertArrayEquals(pairPriceKey3, keyList.get(2));



  }

}