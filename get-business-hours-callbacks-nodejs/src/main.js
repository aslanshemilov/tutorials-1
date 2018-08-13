const request = require('request-promise');
//region Edit constants
// Note:You must provision a Callback execution service and an associated Business Hours 
// service in Designer.
// Start by editing the sample's constants:
// API_BASEPATH is the base URL used to access PureEngage Cloud APIs. 
// API_KEY is the API key provided by Genesys that you must use with all the requests
// to PureEngage Cloud APIs.
const API_BASEPATH = '<API Base path. For example, http://localhost:8080>';
const API_KEY = '<API Key>';
const BUSINESSHOURS_SERVICE = '<Business hours service name>';
const OPENFOR_API_PATH =  '/engagement/v3/callbacks/open-for/' + BUSINESSHOURS_SERVICE;
//endregion

async function getBusinessHoursOpenFor() {

    //region Create the request options.
    // Create the options that are sent as part of the request. These options might vary 
    // slightly according to the client module that is used to handle the HTTP request 
    // (request-promise in this example).
    // In the options, specify the API URL, the method as GET, and the header parameters.
    // The Business Hours service used to query open hours is part of the URL.
    // The header parameter 'Content-Type' must be 'application/json'.
    // The header parameter 'x-api-key' is the API key provided by Genesys to use with all the 
    // requests to PureEngage Cloud APIs. 
    // Additional parameters are available to create a callback. You can find the list of 
    // these optional parameters and detailed descriptions in the Business Hours API Reference.
    const options = {
      method: `GET`,
      uri: API_BASEPATH + OPENFOR_API_PATH,
      headers: {
          'Content-Type': 'application/json',
          'x-api-key': API_KEY
      },
      json: true // Automatically parses the JSON string in the response
    };
    //endregion

    //region Send the request
    // Send the request and parse the results.
    // Congratulations, you are done!
    try {
        const response = await request(options);
        if (!response) {
            console.log('Invalid null or undefined response.');
            return;
        }
        console.log('Business is open for : ' + response.data.openFor + ' seconds' );
    }
    catch (error) {
        console.log('Failed to get business hours. Error : ' + error);
    }
    //endregion
}

getBusinessHoursOpenFor();
