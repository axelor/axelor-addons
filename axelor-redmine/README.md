Overview
------
This module adds feature that helps to import issues from the Redmine's REST API.
All the issues from the Redmine Projects are imported to the ABS with just one button click in batches.

  
Dependencies
------

* axelor-helpdesk
* axelor-project
* redmine-java-api

Configuration
------
* **Authentication** 

>Most of the time, the API requires authentication. To enable the API-style authentication, you have to check Authentication required and Enable REST API in Administration -> Settings -> Authentication & API.
		
* **Requirements**

>1. Title (URI) (i.e. https://www.hostedredmine.com)
>2. API access Key (i.e. alpha-numeric key)
>    - After enabling REST API, you can find your API Access key on your account page ( My Account ) when logged in, on the right-hand pane of the default layout.
>3. Add a Custom Field named isImported in Redmine's REST API : 
>    - In Administration -> Custom Fields -> New Custom Field -> Issues 
>      - Format - Boolean
>      - Name - isImported
>      - Default Value - No
>      - Display - Checkboxes
>      - Used as a filter - check mark it
>      - Trackers - Check all
>      - Projects - Check all
>    - In the end save the changes.
 
 