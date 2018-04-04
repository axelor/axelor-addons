import java.util.List;

import org.junit.Test;

import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.CustomFieldDefinition;

public class TestCustomFields {
	
	@Test
    public void createCustomField() throws RedmineException, InstantiationException, IllegalAccessException {
        RedmineManager mgr=RedmineManagerFactory.createWithUserAuth("http://sos.axelor.com:3000","admin","lmdpdlaa");
        
        System.err.println("===========================================================");
        List<CustomFieldDefinition> cdf = mgr.getCustomFieldManager().getCustomFieldDefinitions();
        for (CustomFieldDefinition customFieldDefinition : cdf) {
            System.err.println(customFieldDefinition.getName()+"\t "+customFieldDefinition.getFieldFormat()+"\t "+customFieldDefinition.getCustomizedType()+"\t ");
        }
        
        System.err.println("---------------------------------------------------");
        try {
			CustomFieldDefinition cdf2 = mgr.getCustomFieldManager().getCustomFieldDefinitions().stream()
					.filter(df -> df.getName().contains("isImported"))
					.filter(df -> df.getCustomizedType().equalsIgnoreCase("issue"))
					.filter(df -> df.getFieldFormat().equalsIgnoreCase("bool"))
					/*.filter(df -> df.isFilter())*/
					.findFirst().orElseThrow(() -> new AxelorException("Please, Check configuration", IException.CONFIGURATION_ERROR));
			System.err.println(""+cdf2.getName()+"\t "+cdf2.getFieldFormat()+"\t "+cdf2.getCustomizedType()+"\t "+cdf2.isFilter());
		} catch (AxelorException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
    }
}
