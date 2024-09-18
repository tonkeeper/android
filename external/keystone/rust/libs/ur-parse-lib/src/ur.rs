use alloc::vec::Vec;
use ur_registry::error::{URError, URResult};
use ur_registry::registry_types::URType;

pub struct UR {
    ur_type: URType,
    data: Vec<u8>,
}

impl UR {
    pub fn new(ur_type: URType, data: Vec<u8>) -> Self {
        UR { ur_type, data }
    }

    pub fn parse<T: TryFrom<Vec<u8>, Error = URError>>(&self) -> URResult<(URType, T)> {
        let result = T::try_from(self.data.clone())?;
        Ok((self.ur_type.clone(), result))
    }
}