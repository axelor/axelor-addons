Overview
------
This module adds feature that helps to import issues from the Redmine's REST API.
All the issues from the Redmine Projects are imported to the ABS with just one button click in batches.

  
Dependencies
------

* axelor-helpdesk
* axelor-project
* redmine-java-api (version used : 3.1.0)

* Configuration
	- uri (i.e. https://www.hostedredmine.com)
	- apiAccessKey (i.e. alpha-numeric key) or login/password
	
	- Add a Custom Field named "isImported" in Redmine's REST API : 
	  In Administration -> Custom Fields -> New Custom Field -> Issues 
		- Format - Boolean
		- Name - isImported
		- Default Value - No
		- Display - Checkboxes
		- Used as a filter - check mark it
		- Trackers - Check all
		- Projects - Check all
	  In the end save the changes.
