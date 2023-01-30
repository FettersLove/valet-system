package com.example.hxds.vhr.db.dao;


import com.example.hxds.common.util.PageUtils;
import com.example.hxds.vhr.db.pojo.VoucherEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public interface VoucherDao {
    public ArrayList<HashMap> searchVoucherByPage(Map param);

    public long searchVoucherCount(Map param);

    public int insert(VoucherEntity entity);

    public HashMap searchVoucherById(long id);

    public int updateVoucherStatus(Map param);

    public ArrayList<HashMap> searchVoucherTakeCount(Long[] ids);

    public int deleteVoucherByIds(Long[] ids);

    public ArrayList<HashMap> searchUnTakeVoucherByPage(Map param);

    public long searchUnTakeVoucherCount(Map param);

    public ArrayList<HashMap> searchUnUseVoucherByPage(Map param);

    public long searchUnUseVoucherCount(Map param);

    public ArrayList<HashMap> searchUsedVoucherByPage(Map param);

    public long searchUsedVoucherCount(Map param);

    public int takeVoucher(long id);

    public int updateUsedCount(long id);

    public HashMap searchBestUnUseVoucher(Map param);
}




