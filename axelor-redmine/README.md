Overview
------
This module adds feature that helps to import issues from the Redmine's REST API.
All the issues from the Redmine Projects are imported to the ABS with just one button click in batches.

  
Dependencies
------

* axelor-helpdesk
* axelor-project
* redmine-java-api (version used : 3.1.0)

Redmine Java API library is a FREE third-party Java library that can be used to access the Redmine API.


Installation of Redmine Java API
------

* Documentation Reference
	- http://www.redmine.org/projects/redmine/wiki/Rest_api
	- https://github.com/taskadapter/redmine-java-api

* Authentication 
	Most of the time, the API requires authentication. To enable the API-style authentication, you have to check Enable REST API in Administration -> Settings -> API. Then, authentication can be done in 2 			different ways:
	- using your regular login/password via HTTP Basic authentication.
	- using your API key which is a handy way to avoid putting a password in a script. 

	You can find your API Access key on your account page ( /my/account ) when logged in, on the right-hand pane of the default layout.

* Requirements
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
			

Sample Code
------

* Get list of issues by specifying Multi-values search

	Params params = new Params()
            .add("f[]", "fieldName")
            .add("op[fieldName]", "Operator to be used on Field")
            .add("v[fieldName][]", "Value of the Field");

	result = issueManager.getIssues(params);

	** cf_x: get issues with the given value for custom field with an ID of x. (Custom field must have 'used as a filter' checked.)	

	- Operator Reference : 
		- = 		equal
		- ! 		not equal
		- * 		all
		- !*		none
		- >= 		greater than or equal
		- <= 		less than or equal
		- ~ 		contains
		- !~ 		does not contain
		- t 		today
		- w 		this week
		- t+ 		in (used with dates, e.g. in exactly 5 days)
		- t- 		ago
		- >t+ 		in more than (used with dates, e.g. in more than 5 days)
		- >t- 		in more than ago (used with dates, e.g. in more than 5 days ago)
		- o 		open (only used on status)
		- c 		closed (only used on status)

