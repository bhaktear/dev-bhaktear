package abl.frd.qremit.converter.nafex.service;
import abl.frd.qremit.converter.nafex.model.*;
import abl.frd.qremit.converter.nafex.repository.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
@SuppressWarnings("unchecked")
@Service
public class NafexModelService {
    @Autowired
    NafexModelRepository nafexModelRepository;
    @Autowired
    OnlineModelRepository onlineModelRepository;
    @Autowired
    CocModelRepository cocModelRepository;
    @Autowired
    AccountPayeeModelRepository accountPayeeModelRepository;
    @Autowired
    BeftnModelRepository beftnModelRepository;
    @Autowired
    FileInfoModelRepository fileInfoModelRepository;
    @Autowired
    UserModelRepository userModelRepository;
    @Autowired
    ExchangeHouseModelRepository exchangeHouseModelRepository;
    @Autowired
    ErrorDataModelService errorDataModelService;
    @Autowired
    FileInfoModelService fileInfoModelService;
    LocalDateTime currentDateTime = LocalDateTime.now();

    public Map<String, Object> save(MultipartFile file, int userId, String exchangeCode, String nrtaCode) {
        Map<String, Object> resp = new HashMap<>();
        try{
            FileInfoModel fileInfoModel = new FileInfoModel();
            fileInfoModel.setUserModel(userModelRepository.findByUserId(userId));
            User user = userModelRepository.findByUserId(userId);
            
            fileInfoModel.setExchangeCode(exchangeCode);
            fileInfoModel.setFileName(file.getOriginalFilename());
            fileInfoModel.setUploadDateTime(currentDateTime);
            fileInfoModelRepository.save(fileInfoModel);
            
            Map<String, Object> nafexData = csvToNafexModels(file.getInputStream(), user, fileInfoModel, exchangeCode, nrtaCode);
            List<NafexEhMstModel> nafexModels = (List<NafexEhMstModel>) nafexData.get("nafexDataModelList");

            if(nafexData.containsKey("errorMessage")){
                resp.put("errorMessage", nafexData.get("errorMessage"));
            }
            if(nafexData.containsKey("errorCount") && ((Integer) nafexData.get("errorCount") >= 1)){
                int errorCount = (Integer) nafexData.get("errorCount");
                fileInfoModel.setErrorCount(errorCount);
                resp.put("fileInfoModel", fileInfoModel);
                fileInfoModelRepository.save(fileInfoModel);
            }

            if(nafexModels.size()!=0) {
                for (NafexEhMstModel nafexModel : nafexModels) {
                    nafexModel.setFileInfoModel(fileInfoModel);
                    nafexModel.setUserModel(user);
                }
                // 4 DIFFERENT DATA TABLE GENERATION GOING ON HERE
                Map<String, Object> convertedDataModels = CommonService.generateFourConvertedDataModel(nafexModels, fileInfoModel, user, currentDateTime, 0);
                fileInfoModel = CommonService.countFourConvertedDataModel(convertedDataModels);
                fileInfoModel.setTotalCount(String.valueOf(nafexModels.size()));
                fileInfoModel.setIsSettlement(0);
                fileInfoModel.setNafexEhMstModel(nafexModels);

                // SAVING TO MySql Data Table
                try{
                    fileInfoModelRepository.save(fileInfoModel);                
                    resp.put("fileInfoModel", fileInfoModel);
                }catch(Exception e){
                    resp.put("errorMessage", e.getMessage());
                }
            }
        } catch (IOException e) {
            String message = "fail to store csv data: " + e.getMessage();
            resp.put("errorMessage", message);
            throw new RuntimeException(message);
        }
        return resp;
    }
    
    public Map<String, Object> csvToNafexModels(InputStream is, User user, FileInfoModel fileInfoModel, String exchangeCode, String nrtaCode) {
        Map<String, Object> resp = new HashMap<>();
        Optional<NafexEhMstModel> duplicateData;
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            CSVParser csvParser = new CSVParser(fileReader, CSVFormat.newFormat('|') .withIgnoreHeaderCase().withTrim())) {
            Iterable<CSVRecord> csvRecords = csvParser.getRecords();
            List<NafexEhMstModel> nafexDataModelList = new ArrayList<>();
            List<ErrorDataModel> errorDataModelList = new ArrayList<>();
            List<String> transactionList = new ArrayList<>();
            String duplicateMessage = "";
            int i = 0;
            int duplicateCount = 0;
            for (CSVRecord csvRecord : csvRecords) {
                i++;
                String transactionNo = csvRecord.get(1).trim();
                String amount = csvRecord.get(3).trim();
                duplicateData = nafexModelRepository.findByTransactionNoIgnoreCaseAndAmountAndExchangeCode(transactionNo, Double.valueOf(amount), exchangeCode);
                String beneficiaryAccount = csvRecord.get(7).trim();
                String bankName = csvRecord.get(8).trim();
                String branchCode = CommonService.fixRoutingNo(csvRecord.get(11).trim());
                Map<String, Object> data = getCsvData(csvRecord, exchangeCode, transactionNo, beneficiaryAccount, bankName, branchCode);

                Map<String, Object> errResp = CommonService.checkError(data, errorDataModelList, nrtaCode, fileInfoModel, user, currentDateTime, csvRecord.get(0).trim(), duplicateData, transactionList);
                if((Integer) errResp.get("err") == 1){
                    errorDataModelList = (List<ErrorDataModel>) errResp.get("errorDataModelList");
                    continue;
                }
                if((Integer) errResp.get("err") == 2){
                    resp.put("errorMessage", errResp.get("msg"));
                    break;
                }
                if((Integer) errResp.get("err") == 3){
                    duplicateMessage += errResp.get("msg");
                    duplicateCount++;
                    continue;
                }
                if((Integer) errResp.get("err") == 4){
                    duplicateMessage += errResp.get("msg");
                    continue;
                }
                if(errResp.containsKey("transactionList"))  transactionList = (List<String>) errResp.get("transactionList");

                /*
                if(duplicateData.isPresent()){  // Checking Duplicate Transaction No in this block
                    duplicateMessage += "Duplicate Reference No " + transactionNo + " Found <br>";
                    duplicateCount++;
                    continue;
                }
                //check exchange code
                exchangeMessage = CommonService.checkExchangeCode(csvRecord.get(0), exchangeCode, nrtaCode);
                if(!exchangeMessage.isEmpty())  break;

                //a/c no, benficiary name, amount empty or null check
                errorMessage = CommonService.checkBeneficiaryNameOrAmountOrBeneficiaryAccount(beneficiaryAccount, csvRecord.get(6), csvRecord.get(3));
                if(!errorMessage.isEmpty()){
                    CommonService.addErrorDataModelList(errorDataModelList, data, exchangeCode, errorMessage, currentDateTime, user, fileInfoModel);
                    continue;
                }
                if(CommonService.isBeftnFound(bankName, beneficiaryAccount, branchCode)){
                    errorMessage = CommonService.checkBEFTNRouting(branchCode);
                    if(!errorMessage.isEmpty()){
                        CommonService.addErrorDataModelList(errorDataModelList, data, exchangeCode, errorMessage, currentDateTime, user, fileInfoModel);
                        continue;
                    }
                }else if(CommonService.isCocFound(beneficiaryAccount)){
                    errorMessage = CommonService.checkCOCBankName(bankName);
                    if(!errorMessage.isEmpty()){
                        CommonService.addErrorDataModelList(errorDataModelList, data, exchangeCode, errorMessage, currentDateTime, user, fileInfoModel);
                        continue;
                    }
                }else if(CommonService.isAccountPayeeFound(bankName, beneficiaryAccount, branchCode)){
                    errorMessage = CommonService.checkABLAccountAndRoutingNo(beneficiaryAccount, branchCode, bankName);
                    if(!errorMessage.isEmpty()){
                        CommonService.addErrorDataModelList(errorDataModelList, data, exchangeCode, errorMessage, currentDateTime, user, fileInfoModel);
                        continue;
                    }
                    errorMessage = CommonService.checkCOString(beneficiaryAccount);
                    if(!errorMessage.isEmpty()){
                        CommonService.addErrorDataModelList(errorDataModelList, data, exchangeCode, errorMessage, currentDateTime, user, fileInfoModel);
                        continue;
                    }
                }else if(CommonService.isOnlineAccoutNumberFound(beneficiaryAccount)){
                    
                }
                if(transactionList.contains(transactionNo)){
                    duplicateMessage += "Duplicate Transaction No " + transactionNo + " Found <br>";
                    continue;
                }else transactionList.add(transactionNo);
                */
                

                NafexEhMstModel nafexDataModel = new NafexEhMstModel();
                nafexDataModel = CommonService.createDataModel(nafexDataModel, data);
                nafexDataModel.setTypeFlag(CommonService.setTypeFlag(beneficiaryAccount, bankName, branchCode));
                nafexDataModel.setUploadDateTime(currentDateTime);
                nafexDataModelList.add(nafexDataModel);
            }
            /*
            if (!errorDataModelList.isEmpty()) {
                try{
                    List<ErrorDataModel> errorDataModels = errorDataModelRepository.saveAll(errorDataModelList);
                    int errorCount = errorDataModels.size();
                    resp.put("errorCount", errorCount);
                }catch(Exception e){
                    resp.put("errorMessage", e.getMessage());
                }
            }
            */
            //save error data
            Map<String, Object> saveError = errorDataModelService.saveErrorModelList(errorDataModelList);
            if(saveError.containsKey("errorCount")) resp.put("errorCount", saveError.get("errorCount"));
            if(saveError.containsKey("errorMessage")){
                resp.put("errorMessage", saveError.get("errorMessage"));
                return resp;
            }
            //if both model is empty then delete fileInfoModel
            if(errorDataModelList.isEmpty() && nafexDataModelList.isEmpty()){
                fileInfoModelService.deleteFileInfoModelById(fileInfoModel.getId());
            }
            resp.put("nafexDataModelList", nafexDataModelList);
            resp.put("errorMessage", CommonService.setErrorMessage(duplicateMessage, duplicateCount, i));
            /* 
            if(!duplicateMessage.isEmpty())  resp.put("errorMessage", duplicateMessage);
            //if(!exchangeMessage.isEmpty())  resp.put("errorMessage", exchangeMessage);
            if(i == duplicateCount){
                resp.put("errorMessage", "All Data From Your Selected File Already Exists!");
            }else if(duplicateCount >= 1) resp.put("errorMessage", duplicateMessage);
            */
            return resp;
        } catch (IOException e) {
            String message = "fail to parse CSV file: " + e.getMessage();
            resp.put("errorMessage", message);
            throw new RuntimeException(message);
        }
    }

    public Map<String, Object> getCsvData(CSVRecord csvRecord, String exchangeCode, String transactionNo, String beneficiaryAccount, String bankName, String branchCode){
        Map<String, Object> data = new HashMap<>();
        data.put("exchangeCode", exchangeCode);
        data.put("transactionNo", transactionNo);
        data.put("currency", csvRecord.get(2));
        data.put("amount", csvRecord.get(3));
        data.put("enteredDate", csvRecord.get(4));
        data.put("remitterName", csvRecord.get(5));
        data.put("remitterMobile", csvRecord.get(17));
        data.put("beneficiaryName", csvRecord.get(6));
        data.put("beneficiaryAccount", beneficiaryAccount);
        data.put("beneficiaryMobile", csvRecord.get(12));
        data.put("bankName", bankName);
        data.put("bankCode", csvRecord.get(9));
        data.put("branchName", csvRecord.get(10));
        data.put("branchCode", branchCode);
        data.put("draweeBranchName", csvRecord.get(13));
        data.put("draweeBranchCode", csvRecord.get(14));
        data.put("purposeOfRemittance", csvRecord.get(15));
        data.put("sourceOfIncome", csvRecord.get(16));
        data.put("processFlag", "");
        data.put("processedBy", "");
        data.put("processedDate", "");
        return data;
    }

}
