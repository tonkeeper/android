use alloc::string::{String, ToString};
use core::fmt;
use ur_registry::error::{URError, URResult};

pub fn probe_encode(
    message: &[u8],
    max_fragment_length: usize,
    ur_type: String,
) -> URResult<UREncodeResult> {
    let mut encoder = ur::Encoder::new(message, max_fragment_length, ur_type.clone())
        .map_err(|e| URError::CborEncodeError(e.to_string()))?;
    if encoder.fragment_count() > 1 {
        Ok(UREncodeResult {
            is_multi_part: true,
            data: encoder
                .next_part()
                .map_err(|e| URError::UrEncodeError(e.to_string()))?,
            encoder: Some(KeystoneUREncoder::new(encoder)),
        })
    } else {
        let ur = ur::encode(message, ur_type);
        Ok(UREncodeResult {
            is_multi_part: false,
            data: ur,
            encoder: None,
        })
    }
}

pub struct UREncodeResult {
    pub is_multi_part: bool,
    pub data: String,
    pub encoder: Option<KeystoneUREncoder>,
}

pub struct KeystoneUREncoder {
    encoder: ur::Encoder,
}

impl fmt::Debug for UREncodeResult {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_tuple("")
            .field(&self.is_multi_part)
            .field(&self.data)
            .finish()
    }
}

impl KeystoneUREncoder {
    pub fn new(encoder: ur::Encoder) -> Self {
        KeystoneUREncoder { encoder }
    }

    pub fn next_part(&mut self) -> URResult<String> {
        self.encoder
            .next_part()
            .map_err(|e| URError::CborEncodeError(e.to_string()))
    }

    pub fn current_index(&self) -> usize {
        self.encoder.current_index()
    }

    pub fn fragment_count(&self) -> usize {
        self.encoder.fragment_count()
    }
}

#[cfg(test)]
mod tests {
    use crate::keystone_ur_encoder::probe_encode;
    use alloc::vec::Vec;
    use hex::FromHex;
    use ur_registry::crypto_psbt::CryptoPSBT;
    use ur_registry::extend::qr_hardware_call::QRHardwareCall;
    use ur_registry::traits::RegistryItem;

    #[test]
    fn test_encode_ada_hardware_call() {
        let data = "a3010002d90515a10182d90516a101d90130a10186182cf500f500f5d90516a201d90130a1018a182cf51901f5f500f500f500f502010400";
        let data = Vec::from_hex(data).unwrap();
        // hardware call
        let res = probe_encode(&data, 400, QRHardwareCall::get_registry_type().get_type()).unwrap();
        assert_eq!(
            "ur:qr-hardware-call/otadaeaotaahbzoyadlftaahcmoyadtaaddyoyadlncsdwykaeykaeyktaahcmoeadtaaddyoyadlecsdwykcfadykykaeykaeykaeykaoadaaaeyteyldre",
            res.data
        )
    }
    //
    #[test]
    fn test_encode_sol_hardware_call() {
        let data = "a4010002d90515a10184d90516a301d90130a10186182cf5183cf500f502000463455448d90516a301d90130a10186182cf51901f5f500f502010463534f4cd90516a301d90130a10186182cf51901f5f501f502000463534f4cd90516a301d90130a1018a182cf51901f5f500f500f400f402010463534f4c036b4c6561702057616c6c65740401";
        let data = Vec::from_hex(data).unwrap();
        // hardware call
        let res = probe_encode(&data, 400, QRHardwareCall::get_registry_type().get_type()).unwrap();
        assert_eq!(
                "ur:qr-hardware-call/oxadaeaotaahbzoyadlrtaahcmotadtaaddyoyadlncsdwykcsfnykaeykaoaeaaiafeghfdtaahcmotadtaaddyoyadlncsdwykcfadykykaeykaoadaaiagugwgstaahcmotadtaaddyoyadlncsdwykcfadykykadykaoaeaaiagugwgstaahcmotadtaaddyoyadlecsdwykcfadykykaeykaewkaewkaoadaaiagugwgsaxjegsihhsjocxhghsjzjzihjyaaadfnfxcmfy",
                res.data
            )
    }
    #[test]
    fn test_encode_cosmos_hardware_call() {
        let data = "a3010002d90515a10182d90516a101d90130a10186182cf500f500f5d90516a201d90130a1018a182cf51901f5f500f500f500f502010400";
        let data = Vec::from_hex(data).unwrap();
        // hardware call
        let res = probe_encode(&data, 400, QRHardwareCall::get_registry_type().get_type()).unwrap();
        assert_eq!(
            "ur:qr-hardware-call/otadaeaotaahbzoyadlftaahcmoyadtaaddyoyadlncsdwykaeykaeyktaahcmoeadtaaddyoyadlecsdwykcfadykykaeykaeykaeykaoadaaaeyteyldre",
            res.data
        )
    }

    #[test]
    fn test_encode() {
        let crypto = CryptoPSBT::new(
            Vec::from_hex("8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa8c05c4b4f3e88840a4f4b5f155cfd69473ea169f3d0431b7a6787a23777f08aa")
                .unwrap());
        let result: Vec<u8> = crypto.try_into().unwrap();
        let result =
            probe_encode(&result, 400, CryptoPSBT::get_registry_type().get_type()).unwrap();
        assert_eq!("ur:crypto-psbt/1-3/lpadaxcfaxiacyvwhdfhndhkadclhkaxhnlkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbnychpmiy",
                   result.data);
        if result.is_multi_part {
            let mut encoder = result.encoder.unwrap();
            let next = encoder.next_part().unwrap();
            assert_eq!("ur:crypto-psbt/2-3/lpaoaxcfaxiacyvwhdfhndhkadclaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaylbntahvo",
                       next);
            let next = encoder.next_part().unwrap();
            assert_eq!("ur:crypto-psbt/3-3/lpaxaxcfaxiacyvwhdfhndhkadclpklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypknseoskve",
                       next
            );
            let next = encoder.next_part().unwrap();
            assert_eq!("ur:crypto-psbt/4-3/lpaaaxcfaxiacyvwhdfhndhkadclaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbaypklkahssqzwfvslofzoxwkrewngotktbmwjkwdcmnefsaaehrlolkskncnktlbayneieyksn",
                       next);
        }
    }
}