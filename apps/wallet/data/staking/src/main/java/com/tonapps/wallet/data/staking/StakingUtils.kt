package com.tonapps.wallet.data.staking

import org.ton.bitstring.MutableBitString
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.cell.buildCell
import org.ton.tlb.storeTlb
import java.math.BigInteger

object StakingUtils {

    fun createWhalesAddStakeCommand(
        queryId: BigInteger
    ): Cell {
        return buildCell {
            storeUInt(3665837821, 32)
            storeUInt(queryId, 64)
            storeTlb(Coins, Coins.ofNano(100000))
        }
    }

    fun createWhalesWithdrawStakeCell(
        queryId: BigInteger,
        amount: Coins
    ): Cell {
        return buildCell {
            storeUInt(3665837821, 32)
            storeUInt(queryId, 64)
            storeTlb(Coins, Coins.ofNano(100000))
            storeTlb(Coins, amount)
        }
    }

    fun createLiquidTfAddStakeCommand(queryId: BigInteger): Cell {
        return buildCell {
            storeUInt(0x47d54391, 32)
            storeUInt(queryId, 64)
            storeUInt(0x000000000005b7ce, 64)
        }
    }

    fun createLiquidTfWithdrawStakeCell(
        queryId: BigInteger,
        amount: Coins,
        address: AddrStd
    ): Cell {
        val customPayload = buildCell {
            storeUInt(1, 1)
            storeUInt(0, 1)
        }

        return buildCell {
            storeUInt(0x595f07bc, 32)
            storeUInt(queryId, 64)
            storeTlb(Coins, amount)
            storeTlb(AddrStd, address)
            storeBit(true)
            refs.add(customPayload)
        }
    }

    fun createTfAddStakeCommand(): Cell {
        return buildCell {
            storeUInt(0, 32)
            storeBytes("d".toByteArray())
        }
    }

    fun createTfWithdrawStakeCell(): Cell {
        return buildCell {
            storeUInt(0, 32)
            storeBytes("w".toByteArray())
        }
    }
}

/*
export const getStakeSignRawMessage = async (
  pool: PoolInfo,
  amount: BN,
  transactionType: StakingTransactionType,
  responseAddress: string,
  isSendAll?: boolean,
  stakingJetton?: JettonBalanceModel,
): Promise<SignRawMessage> => {
  const withdrawalFee = getWithdrawalFee(pool);

  const address = Address.parse(
    stakingJetton && transactionType !== StakingTransactionType.DEPOSIT
      ? stakingJetton.walletAddress
      : pool.address,
  ).toFriendly({ bounceable: true });

  if (pool.implementation === PoolImplementationType.Whales) {
    const payload =
      transactionType === StakingTransactionType.DEPOSIT
        ? await createWhalesAddStakeCommand()
        : await createWhalesWithdrawStakeCell(isSendAll ? Ton.toNano(0) : amount);

    return {
      address,
      amount: transactionType === StakingTransactionType.DEPOSIT ? amount : withdrawalFee,
      payload,
    };
  }

  if (pool.implementation === PoolImplementationType.LiquidTF) {
    const payload =
      transactionType === StakingTransactionType.DEPOSIT
        ? await createLiquidTfAddStakeCommand()
        : await createLiquidTfWithdrawStakeCell(amount, responseAddress);

    const amountWithFee = Ton.toNano(
      new BigNumber(Ton.fromNano(amount)).plus(Ton.fromNano(withdrawalFee)).toString(),
    );

    const depositAmount =
      pool.implementation === PoolImplementationType.LiquidTF && !isSendAll
        ? amountWithFee
        : amount;

    return {
      address,
      amount:
        transactionType === StakingTransactionType.DEPOSIT
          ? depositAmount
          : withdrawalFee,
      payload,
    };
  }

  if (pool.implementation === PoolImplementationType.Tf) {
    const payload =
      transactionType === StakingTransactionType.DEPOSIT
        ? await createTfAddStakeCommand()
        : await createTfWithdrawStakeCell();

    return {
      address,
      amount: transactionType === StakingTransactionType.DEPOSIT ? amount : withdrawalFee,
      payload,
    };
  }

  throw new Error('not implemented yet');
};
 */